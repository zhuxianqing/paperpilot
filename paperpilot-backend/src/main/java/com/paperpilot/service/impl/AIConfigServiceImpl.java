package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperpilot.dto.request.AIConfigRequest;
import com.paperpilot.dto.response.AIConfigVO;
import com.paperpilot.entity.UserAIConfig;
import com.paperpilot.mapper.UserAIConfigMapper;
import com.paperpilot.service.AIConfigService;
import com.paperpilot.util.AESUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIConfigServiceImpl implements AIConfigService {

    private final UserAIConfigMapper aiConfigMapper;
    private final AESUtil aesUtil;

    @Override
    public List<AIConfigVO> getUserConfigs(Long userId) {
        LambdaQueryWrapper<UserAIConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAIConfig::getUserId, userId)
                .eq(UserAIConfig::getIsActive, true);

        List<UserAIConfig> configs = aiConfigMapper.selectList(wrapper);

        return configs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveConfig(Long userId, AIConfigRequest request) {
        try {
            // 加密API Key
            String encryptedKey = aesUtil.encrypt(request.getApiKey());

            // 检查是否已存在
            LambdaQueryWrapper<UserAIConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserAIConfig::getUserId, userId)
                    .eq(UserAIConfig::getProvider, request.getProvider());

            UserAIConfig existing = aiConfigMapper.selectOne(wrapper);

            if (existing != null) {
                // 更新
                existing.setApiKey(encryptedKey);
                existing.setBaseUrl(request.getBaseUrl());
                existing.setModel(request.getModel());
                existing.setIsActive(true);
                aiConfigMapper.updateById(existing);
            } else {
                // 新增
                UserAIConfig config = new UserAIConfig();
                config.setUserId(userId);
                config.setProvider(request.getProvider());
                config.setApiKey(encryptedKey);
                config.setBaseUrl(request.getBaseUrl());
                config.setModel(request.getModel());
                config.setIsActive(true);
                aiConfigMapper.insert(config);
            }

            // 如果设为默认，取消其他默认配置
            if (Boolean.TRUE.equals(request.getIsDefault())) {
                aiConfigMapper.updateDefaultConfig(userId, request.getProvider());
            }

        } catch (Exception e) {
            log.error("Failed to save AI config", e);
            throw new RuntimeException("保存AI配置失败");
        }
    }

    @Override
    @Transactional
    public void deleteConfig(Long userId, String provider) {
        LambdaQueryWrapper<UserAIConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAIConfig::getUserId, userId)
                .eq(UserAIConfig::getProvider, provider);

        UserAIConfig config = aiConfigMapper.selectOne(wrapper);
        if (config != null) {
            config.setIsActive(false);
            aiConfigMapper.updateById(config);
        }
    }

    private AIConfigVO convertToVO(UserAIConfig config) {
        return AIConfigVO.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .model(config.getModel())
                .isActive(config.getIsActive())
                .lastTestedAt(config.getLastTestedAt())
                .testStatus(config.getTestStatus())
                .build();
    }
}
