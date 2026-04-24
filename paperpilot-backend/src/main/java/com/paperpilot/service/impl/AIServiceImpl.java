package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.response.PaperAnalysisTranslateVO;
import com.paperpilot.dto.response.PaperAnalysisVO;
import com.paperpilot.entity.UserAIConfig;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.mapper.UserAIConfigMapper;
import com.paperpilot.service.AICostService;
import com.paperpilot.service.AIService;
import com.paperpilot.service.CacheService;
import com.paperpilot.service.QuotaService;
import com.paperpilot.util.AESUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final WebClient webClient;
    private final UserAIConfigMapper aiConfigMapper;
    private final AESUtil aesUtil;
    private final QuotaService quotaService;
    private final CacheService cacheService;
    private final AICostService aiCostService;

    @Value("${ai.provider}")
    private String systemProvider;

    @Value("${ai.api-key}")
    private String systemApiKey;

    @Value("${ai.model}")
    private String systemModel;

    @Value("${ai.base-url}")
    private String systemBaseUrl;

    // 限流配置
    private static final int RATE_LIMIT_PER_MINUTE = 5;  // 每分钟最多5次
    private static final int RATE_LIMIT_WINDOW = 60;     // 60秒窗口


    /**
     * 批量分析，支持缓存和去重
     */
    private List<PaperAnalysisVO> analyzeBatchWithCache(Long userId, AIConfigDTO config, List<PaperDTO> papers) {
        // 步骤1: 按摘要哈希分组去重
        Map<String, List<PaperDTO>> groupedByHash = papers.stream()
                .collect(Collectors.groupingBy(
                        p -> cacheService.sha256Hash(p.getAbstracts() != null ? p.getAbstracts() : p.getTitle()),
                        HashMap::new, Collectors.toList()
                ));

        List<PaperAnalysisVO> results = new ArrayList<>();
        List<PaperDTO> toProcess = new ArrayList<>();

        // 步骤2: 批量缓存查找
        for (Map.Entry<String, List<PaperDTO>> entry : groupedByHash.entrySet()) {
            String hash = entry.getKey();
            PaperDTO firstPaper = entry.getValue().get(0);

            // 使用与分组相同的fallback逻辑生成缓存key
            String cacheKeyText = firstPaper.getAbstracts() != null ? firstPaper.getAbstracts() : firstPaper.getTitle();

            // 尝试从缓存获取
            PaperAnalysisVO cached = cacheService.getCachedAnalysis(cacheKeyText, PaperAnalysisVO.class);

            if (cached != null) {
                // 缓存命中，为所有相同摘要的文献复用结果
                log.debug("Cache hit for paper: {}", firstPaper.getTitle());
                for (PaperDTO p : entry.getValue()) {
                    PaperAnalysisVO copy = copyWithPaperInfo(cached, p.getDoi(), p.getTitle());
                    results.add(copy);

                    // 记录缓存命中日志（不扣费）
                    aiCostService.logAIRequest(userId, config.getProvider(), config.getModel(),
                            0, 0, hash, true, 0, "success", null);
                }
            } else {
                // 需要处理（只取第一篇）
                toProcess.add(firstPaper);
            }
        }

        // 步骤3: 批量调用AI
        List<PaperAnalysisVO> aiResults = new ArrayList<>();
        for (PaperDTO paper : toProcess) {
            long startTime = System.currentTimeMillis();
            try {
                PaperAnalysisVO analysis = analyzeSingle(config, paper);
                aiResults.add(analysis);

                // 缓存结果（使用与分组相同的fallback逻辑）
                String cacheKeyText = paper.getAbstracts() != null ? paper.getAbstracts() : paper.getTitle();
                cacheService.cacheAnalysis(cacheKeyText, analysis, 30);

                // 记录调用日志
                int responseTime = (int) (System.currentTimeMillis() - startTime);
                String hash = cacheService.sha256Hash(paper.getAbstracts() != null ? paper.getAbstracts() : paper.getTitle());
                aiCostService.logAIRequest(userId, config.getProvider(), config.getModel(),
                        estimateTokens(paper), 500, hash, false, responseTime, "success", null);

            } catch (Exception e) {
                log.error("AI analysis failed for paper: {}", paper.getTitle(), e);
                int responseTime = (int) (System.currentTimeMillis() - startTime);
                String hash = cacheService.sha256Hash(paper.getAbstracts() != null ? paper.getAbstracts() : paper.getTitle());
                aiCostService.logAIRequest(userId, config.getProvider(), config.getModel(),
                        estimateTokens(paper), 0, hash, false, responseTime, "failed", e.getMessage());

                aiResults.add(PaperAnalysisVO.builder()
                        .title(paper.getTitle())
                        .aiSummary("分析失败，请重试")
                        .build());
            }
        }

        // 步骤4: 将AI结果映射回原始文献
        int aiResultIndex = 0;
        for (Map.Entry<String, List<PaperDTO>> entry : groupedByHash.entrySet()) {
            String hash = entry.getKey();
            PaperDTO firstPaper = entry.getValue().get(0);

            // 检查是否已处理（缓存未命中）
            if (toProcess.contains(firstPaper)) {
                PaperAnalysisVO aiResult = aiResults.get(aiResultIndex++);
                for (PaperDTO p : entry.getValue()) {
                    results.add(copyWithPaperInfo(aiResult, p.getDoi(), p.getTitle()));
                }
            }
        }

        return results;
    }

    /**
     * 复制分析结果，使用原始文献的标识信息
     */
    private PaperAnalysisVO copyWithPaperInfo(PaperAnalysisVO source, String doi, String title) {
        return PaperAnalysisVO.builder()
                .doi(doi)
                .title(title)
                .aiSummary(source.getAiSummary())
                .keyPoints(source.getKeyPoints())
                .methodology(source.getMethodology())
                .conclusion(source.getConclusion())
                .researchFindings(source.getResearchFindings())
                .aiKeywords(source.getAiKeywords())
                .build();
    }

    /**
     * 估算token数（粗略估计）
     */
    private int estimateTokens(PaperDTO paper) {
        int textLength = paper.getTitle().length();
        if (paper.getAbstracts() != null) {
            textLength += paper.getAbstracts().length();
        }
        // 粗略估算：1个token约4个字符
        return textLength / 4 + 500; // 加上prompt的token
    }

    @Override
    public boolean testAIConfig(AIConfigDTO configDTO) {
        try {
            AIConfigDTO config = AIConfigDTO.builder()
                    .provider(configDTO.getProvider())
                    .apiKey(configDTO.getApiKey())
                    .baseUrl(configDTO.getBaseUrl())
                    .model(configDTO.getModel())
                    .build();

            String response = callAIAPI(config, "Hello, this is a test. Please reply 'OK'.");
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.error("AI config test failed", e);
            return false;
        }
    }

    @Override
    public PaperAnalysisTranslateVO analyzeSingleWithTranslations(Long userId, PaperDTO paper, boolean useUserConfig, String provider, String model) {
        if (!cacheService.checkRateLimit(userId, RATE_LIMIT_PER_MINUTE, RATE_LIMIT_WINDOW)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        AIConfigDTO config;
        if (useUserConfig) {
            config = getUserAIConfig(userId);
            if (provider != null && !provider.isBlank()) {
                config.setProvider(provider);
            }
            if (model != null && !model.isBlank()) {
                config.setModel(model);
            }
            if (!quotaService.checkByokDailyLimit(userId)) {
                throw new BusinessException(ErrorCode.BYOK_DAILY_LIMIT_EXCEEDED);
            }
        } else {
            config = AIConfigDTO.builder()
                    .provider(systemProvider)
                    .apiKey(systemApiKey)
                    .baseUrl(systemBaseUrl)
                    .model(systemModel)
                    .build();
        }

        String prompt = buildAnalysisPromptWithTranslations(paper);
        String response = callAIAPI(config, prompt);
        return parseAnalysisTranslateResponse(paper, response);
    }

    private PaperAnalysisVO analyzeSingle(AIConfigDTO config, PaperDTO paper) {
        String prompt = buildAnalysisPrompt(paper);
        String response = callAIAPI(config, prompt);
        return parseAnalysisResponse(paper.getDoi(), paper.getTitle(), response);
    }

    private String callAIAPI(AIConfigDTO config, String prompt) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.deepseek.com/v1";
        boolean isAnthropic = baseUrl.contains("anthropic");

        if (isAnthropic) {
            return callAnthropicAPI(config, prompt);
        } else {
            return callOpenAIAPI(config, prompt);
        }
    }

    private String callOpenAIAPI(AIConfigDTO config, String prompt) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.minimaxi.com/v1";

        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "你是一个专业的学术文献分析助手。请分析用户提供的文献，提取关键信息。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 2000
        );

        String rawResponse = webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (rawResponse == null) {
            throw new RuntimeException("AI API returned empty response");
        }

        // 检查是否包含错误信息
        if (rawResponse.contains("\"error\"")) {
            log.error("AI API returned error: {}", rawResponse);
            throw new RuntimeException("AI API error: " + rawResponse);
        }

        // 解析response
        try {
            Map<String, Object> map = new ObjectMapper().readValue(rawResponse, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    Object content = message.get("content");
                    return content != null ? content.toString() : "";
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", rawResponse, e);
        }
        return "";
    }

    private String callAnthropicAPI(AIConfigDTO config, String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("max_tokens", 2000);
        requestBody.put("system", "你是一个专业的学术文献分析助手。请分析用户提供的文献，提取关键信息。");
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        return webClient.post()
                .uri(config.getBaseUrl() + "/v1/messages")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
                    if (content != null && !content.isEmpty()) {
                        // Anthropic 返回的 content 是数组，可能包含 thinking 和 text
                        // 过滤出 type=text 的内容并拼接
                        StringBuilder result = new StringBuilder();
                        for (Map<String, Object> item : content) {
                            if ("text".equals(item.get("type"))) {
                                result.append(item.get("text"));
                            }
                        }
                        return result.toString();
                    }
                    return "";
                })
                .block();
    }

    private String buildAnalysisPrompt(PaperDTO paper) {
        return String.format("""
                请分析以下学术论文，提取关键信息并以JSON格式返回：

                标题：%s
                作者：%s
                摘要：%s
                期刊：%s
                年份：%d

                请返回以下格式的JSON：
                {
                    "summary": "一句话核心贡献（50字以内）",
                    "keyPoints": ["要点1", "要点2", "要点3"],
                    "methodology": "研究方法",
                    "conclusion": "主要结论",
                    "researchFindings": "研究成果",
                    "keywords": ["关键词1", "关键词2", "关键词3"]
                }
                """,
                paper.getTitle(),
                String.join(", ", paper.getAuthors()),
                paper.getAbstracts(),
                paper.getJournal(),
                paper.getPublishYear()
        );
    }

    private String buildAnalysisPromptWithTranslations(PaperDTO paper) {
        return String.format("""
                请分析以下学术论文，并同时完成中文翻译。请严格以JSON格式返回，不要输出任何额外说明。

                标题：%s
                作者：%s
                摘要：%s
                期刊：%s
                年份：%d

                请返回以下格式的JSON：
                {
                    "titleZh": "标题中文翻译",
                    "abstractZh": "摘要中文翻译",
                    "summaryZh": "AI中文总结",
                    "methodologyZh": "研究方法中文总结",
                    "conclusionZh": "研究结论中文总结",
                    "researchFindingsZh": "研究成果中文总结",
                    "keywordsZh": ["关键词1", "关键词2", "关键词3"]
                }
                """,
                paper.getTitle(),
                String.join(", ", paper.getAuthors()),
                paper.getAbstracts(),
                paper.getJournal(),
                paper.getPublishYear() == null ? 0 : paper.getPublishYear()
        );
    }

    private PaperAnalysisVO parseAnalysisResponse(String doi, String title, String response) {
        if (response == null || response.isEmpty()) {
            log.error("AI response is null or empty for paper: {}", title);
            return PaperAnalysisVO.builder()
                    .doi(doi)
                    .title(title)
                    .aiSummary("分析失败：AI返回空响应")
                    .build();
        }
        try {
            // 提取JSON部分
            String jsonStr = extractJson(response);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonStr);

            return PaperAnalysisVO.builder()
                    .doi(doi)
                    .title(title)
                    .aiSummary(node.path("summary").asText())
                    .keyPoints(convertToList(node.path("keyPoints")))
                    .methodology(node.path("methodology").asText())
                    .conclusion(node.path("conclusion").asText())
                    .researchFindings(node.path("researchFindings").asText())
                    .aiKeywords(convertToList(node.path("keywords")))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            return PaperAnalysisVO.builder()
                    .doi(doi)
                    .title(title)
                    .aiSummary(response.substring(0, Math.min(200, response.length())))
                    .build();
        }
    }

    private AIConfigDTO getUserAIConfig(Long userId) {
        LambdaQueryWrapper<UserAIConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAIConfig::getUserId, userId)
                .eq(UserAIConfig::getIsActive, true);

        UserAIConfig config = aiConfigMapper.selectOne(wrapper);
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_CONFIG_NOT_FOUND);
        }

        try {
            String decryptedKey = aesUtil.decrypt(config.getApiKey());
            return AIConfigDTO.builder()
                    .provider(config.getProvider())
                    .apiKey(decryptedKey)
                    .baseUrl(config.getBaseUrl())
                    .model(config.getModel())
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CONFIG_DECRYPT_ERROR);
        }
    }

    private PaperAnalysisTranslateVO parseAnalysisTranslateResponse(PaperDTO paper, String response) {
        if (response == null || response.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED, "AI返回空响应");
        }
        try {
            String jsonStr = extractJson(response);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonStr);
            return PaperAnalysisTranslateVO.builder()
                    .doi(paper.getDoi())
                    .title(paper.getTitle())
                    .titleCn(node.path("titleZh").asText())
                    .abstractText(paper.getAbstracts())
                    .abstractCn(node.path("abstractZh").asText())
                    .summaryZh(node.path("summaryZh").asText())
                    .methodologyZh(node.path("methodologyZh").asText())
                    .conclusionZh(node.path("conclusionZh").asText())
                    .researchFindingsZh(node.path("researchFindingsZh").asText())
                    .aiKeywords(convertToList(node.path("keywordsZh")))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse translated AI response", e);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED, "AI分析结果解析失败");
        }
    }

    @Override
    public String sha256Hash(String input) {
        return cacheService.sha256Hash(input);
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<String> convertToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
