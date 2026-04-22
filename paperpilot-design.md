# PaperPilot 学术文献助手 - 详细设计文档

> 本文档用于指导 AI 代码生成工具（Google AI Studio / Claude / Cursor）生成完整项目代码
> 版本: v1.1
> 日期: 2026-04-06
> 
> **变更记录**: 
> - v1.1 (2026-04-06): 砍掉Node爬虫服务，改为纯前端读取；新增BYOK功能；导出支持飞书文档+Excel双方案

---

## 1. 项目概述

### 1.1 产品定位
一款 Chrome 浏览器插件，帮助科研人员自动检索 Web of Science (WOS) 和 ScienceDirect (SD) 文献，通过 AI 智能总结、筛选（Q1 影响因子过滤），并支持导出 Excel。

### 1.2 商业模式
- 免费层: 每日 3 次免费搜索
- 付费层: 按次计费或订阅会员
- 支付: 集成支付宝/微信支付

### 1.3 技术栈选型
| 层级 | 技术 | 说明 |
|------|------|------|
| 前端(插件) | Chrome Extension Manifest V3 + TypeScript + TailwindCSS | 浏览器插件标准 |
| 后端服务 | Spring Boot 3.x + Java 17 | 用户管理、支付、业务逻辑 |
| ~~爬虫服务~~ | ~~Node.js 20 + Puppeteer + Express~~ | **已砍掉** - 改为插件直接读取WOS页面 |
| 数据库 | MySQL 8.0 | 用户、订单、文献数据、用户AI配置 |
| 缓存 | Redis 7.x | Token、限流、会话、BYOK用户调用计数 |
| AI 接口 | OpenAI/Claude/DeepSeek API | 系统默认 + 用户自带Key(BYOK) |
| 导出存储 | 飞书云文档 / MinIO临时存储 | 双方案支持 |
| 支付 | 支付宝沙箱 → 正式环境 | 个人验证期用第三方聚合支付 |

---

## 2. 系统架构

### 2.1 整体架构图 (v1.1 - 已简化)
```
┌─────────────────────────────────────────────────────────────┐
│                    Chrome Extension                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Content      │  │ Popup        │  │ Background       │  │
│  │ Script       │  │ UI           │  │ Service Worker   │  │
│  │ (读取WOS     │  │ (文献选择/   │  │ (API通信/        │  │
│  │  当前页数据) │  │  结果展示)   │  │  数据暂存)       │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   API Gateway (Nginx)                       │
│              SSL终止 / 负载均衡 / 静态资源                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     Java Backend                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ 用户/认证    │  │ AI服务       │  │ 导出服务         │  │
│  │ 支付/额度    │  │ (系统/BYOK)  │  │ (飞书/Excel)     │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│    MySQL      │  │    Redis      │  │    MinIO      │
│   (主数据库)   │  │   (缓存)      │  │  (临时存储)   │
└───────────────┘  └───────────────┘  └───────────────┘
```

### 2.2 服务职责划分

#### Chrome Extension (插件端)
- **Content Script**: 
    - **注入模式**: 采用 `MAIN` 环境脚本注入（World: MAIN），绕过插件隔离限制。
    - **请求劫持 (Fetch Hook)**: 重写 `window.fetch`，拦截并克隆 WOS 核心 API (`*/api/wosnx/core/runQuerySearch*`) 的响应报文。
    - **通信机制**: 劫持脚本通过 `window.postMessage` 将解析后的 NDJSON 数据实时传送至插件隔离环境（ISOLATED），确保数据获取的零延迟与高准确性。
    - **优势**: 不依赖页面 DOM 结构，彻底免疫页面改版导致的元素定位失效。
- **Popup UI**: 文献选择界面（勾选要分析的论文）、导出方式选择
- **Background**: API通信、用户配置存储、临时数据缓存

#### Java Backend (主服务)
- 用户注册/登录/认证
- JWT Token 签发与验证
- 用户AI配置管理（BYOK功能）
- 支付订单处理
- 额度管理（扣减/查询）
- AI服务（系统API + 用户自带Key）
- 导出服务（飞书文档 / Excel生成）

**已移除**: Node Scraper服务（改为前端直接读取页面，无需后端爬虫）

---

## 3. 数据库设计

### 3.1 表结构定义

```sql
-- 用户表
CREATE TABLE `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `email` VARCHAR(255) NOT NULL COMMENT '邮箱（唯一）',
    `password_hash` VARCHAR(255) NOT NULL COMMENT ' bcrypt 加密密码',
    `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `quota_balance` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '剩余额度（次数）',
    `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否VIP',
    `vip_expire_at` DATETIME DEFAULT NULL COMMENT 'VIP过期时间',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- API密钥表（用于插件认证）
CREATE TABLE `api_keys` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关联用户',
    `api_key` VARCHAR(64) NOT NULL COMMENT 'API Key（随机字符串）',
    `name` VARCHAR(100) DEFAULT 'default' COMMENT 'Key名称',
    `last_used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API密钥表';

-- 用户自定义AI配置表 (BYOK功能)
CREATE TABLE `user_ai_configs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `provider` VARCHAR(20) NOT NULL COMMENT '提供商: openai/deepseek/claude/glm/custom',
    `api_key` VARCHAR(255) NOT NULL COMMENT 'API Key (AES加密存储)',
    `base_url` VARCHAR(500) DEFAULT NULL COMMENT '自定义Base URL',
    `model` VARCHAR(100) NOT NULL COMMENT '模型名: gpt-4/deepseek-chat等',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `last_tested_at` DATETIME DEFAULT NULL COMMENT '上次测试时间',
    `test_status` VARCHAR(20) DEFAULT NULL COMMENT '测试结果: success/failed',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_provider` (`user_id`, `provider`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义AI配置表';
CREATE TABLE `orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（唯一）',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额（元）',
    `quota_amount` INT UNSIGNED NOT NULL COMMENT '购买的额度数量',
    `channel` VARCHAR(20) NOT NULL COMMENT '支付渠道: alipay/wechat',
    `channel_order_no` VARCHAR(64) DEFAULT NULL COMMENT '第三方支付订单号',
    `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付 1-已支付 2-已取消',
    `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
    `expire_at` DATETIME NOT NULL COMMENT '订单过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_channel_order` (`channel`, `channel_order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- 额度变动记录表
CREATE TABLE `quota_transactions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `amount` INT NOT NULL COMMENT '变动数量（正数增加，负数减少）',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: recharge/consume/refund/bonus',
    `order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID',
    `task_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联任务ID（消费时）',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度变动记录';

-- 文献搜索任务表
CREATE TABLE `search_tasks` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `task_no` VARCHAR(32) NOT NULL COMMENT '任务编号',
    `source` VARCHAR(20) NOT NULL COMMENT '来源: wos/sciencedirect/semanticscholar',
    `keyword` VARCHAR(255) NOT NULL COMMENT '搜索关键词',
    `filters` JSON DEFAULT NULL COMMENT '筛选条件（JSON格式）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/running/completed/failed',
    `total_count` INT UNSIGNED DEFAULT 0 COMMENT '总文献数',
    `processed_count` INT UNSIGNED DEFAULT 0 COMMENT '已处理数',
    `quota_consumed` INT UNSIGNED DEFAULT 0 COMMENT '消耗的额度',
    `result_file_url` VARCHAR(500) DEFAULT NULL COMMENT '结果Excel文件URL',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献搜索任务表';

-- 文献详情表
CREATE TABLE `papers` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '关联任务ID',
    `doi` VARCHAR(100) DEFAULT NULL COMMENT 'DOI',
    `title` VARCHAR(500) NOT NULL COMMENT '标题',
    `authors` JSON NOT NULL COMMENT '作者列表（JSON数组）',
    `abstract` TEXT DEFAULT NULL COMMENT '摘要',
    `journal` VARCHAR(255) DEFAULT NULL COMMENT '期刊名',
    `publish_year` INT DEFAULT NULL COMMENT '发表年份',
    `impact_factor` DECIMAL(5,2) DEFAULT NULL COMMENT '影响因子',
    `quartile` VARCHAR(10) DEFAULT NULL COMMENT '分区: Q1/Q2/Q3/Q4',
    `citations` INT UNSIGNED DEFAULT 0 COMMENT '被引次数',
    `pdf_url` VARCHAR(500) DEFAULT NULL COMMENT 'PDF链接',
    `source_url` VARCHAR(500) NOT NULL COMMENT '原文链接',
    `ai_summary` TEXT DEFAULT NULL COMMENT 'AI总结',
    `ai_keywords` JSON DEFAULT NULL COMMENT 'AI提取关键词',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_doi` (`doi`),
    KEY `idx_quartile` (`quartile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献详情表';
```

### 3.2 Redis Key 设计
```
# Token 存储
auth:token:{jti} -> user_id (TTL: 7天)

# 限流（滑动窗口）
rate_limit:{user_id}:{minute} -> count (TTL: 60s)

# 任务状态缓存
task:status:{task_no} -> JSON (TTL: 1小时)

# 支付订单防重
order:processing:{order_no} -> 1 (TTL: 30s)

# 每日免费额度
free_quota:{user_id}:{date} -> used_count (TTL: 24h)
```

---

## 4. Java Backend API 设计

### 4.1 项目结构
```
paperpilot-backend/
├── src/main/java/com/paperpilot/
│   ├── PaperPilotApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security 配置
│   │   ├── RedisConfig.java
│   │   ├── MyBatisConfig.java
│   │   └── WebClientConfig.java         # HTTP 客户端配置
│   ├── controller/
│   │   ├── AuthController.java          # 登录/注册
│   │   ├── UserController.java          # 用户信息
│   │   ├── OrderController.java         # 支付订单
│   │   └── TaskController.java          # 文献任务
│   ├── service/
│   │   ├── UserService.java
│   │   ├── AuthService.java
│   │   ├── OrderService.java
│   │   ├── PaymentService.java          # 支付接口抽象
│   │   ├── TaskService.java
│   │   ├── QuotaService.java            # 额度管理
│   │   ├── ScraperService.java          # 调用Node爬虫
│   │   └── AIService.java               # 调用AI接口
│   ├── service/impl/
│   │   └── ...
│   ├── mapper/
│   │   └── (MyBatis Mapper 接口)
│   ├── entity/
│   │   └── (数据库实体类)
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── vo/
│   │   └── (视图对象)
│   ├── security/
│   │   ├── JwtTokenProvider.java        # JWT 工具
│   │   ├── JwtAuthenticationFilter.java # JWT 过滤器
│   │   └── UserDetailsServiceImpl.java
│   ├── exception/
│   │   └── GlobalExceptionHandler.java
│   └── util/
│       └── ...
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── mapper/
│       └── (XML映射文件)
└── pom.xml
```

### 4.2 核心 API 接口

#### 认证模块
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    /**
     * 发送注册验证码
     * 逻辑: 校验格式 -> 生成6位随机数 -> 存入Redis(5-10min过期) -> 异步发送邮件
     * 安全: 同一邮箱60s限发一次, 同一IP每日限额
     */
    @PostMapping("/send-code")
    public Result<Void> sendRegisterCode(@RequestParam @Email String email);

    /**
     * 邮箱注册
     * 逻辑: 校验Redis验证码 -> BCrypt加密密码 -> 创建用户 -> 初始化额度 -> 清除验证码
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody @Valid RegisterRequest request);
    
    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody @Valid LoginRequest request);
    
    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken);
    
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal UserDetails user);
}

// DTO 定义
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank String code,        // 邮箱验证码
    @NotBlank @Size(min=6) String password,
    String nickname
) {}

public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) {}

public record AuthResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserVO user
) {}
```

#### 用户模块
```java
@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    
    @GetMapping("/profile")
    public Result<UserVO> getProfile(@AuthenticationPrincipal UserDetails user);
    
    @GetMapping("/quota")
    public Result<QuotaVO> getQuota(@AuthenticationPrincipal UserDetails user);
    
    @GetMapping("/transactions")
    public Result<PageResult<QuotaTransactionVO>> getTransactions(
        @AuthenticationPrincipal UserDetails user,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int size
    );
    
    @PostMapping("/api-keys")
    public Result<ApiKeyVO> createApiKey(
        @AuthenticationPrincipal UserDetails user,
        @RequestBody CreateApiKeyRequest request
    );
    
    @GetMapping("/api-keys")
    public Result<List<ApiKeyVO>> listApiKeys(@AuthenticationPrincipal UserDetails user);
    
    @DeleteMapping("/api-keys/{keyId}")
    public Result<Void> revokeApiKey(
        @AuthenticationPrincipal UserDetails user,
        @PathVariable Long keyId
    );
}
```

#### 支付模块
```java
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {
    
    @PostMapping("/orders")
    public Result<OrderVO> createOrder(
        @AuthenticationPrincipal UserDetails user,
        @RequestBody @Valid CreateOrderRequest request
    );
    
    @GetMapping("/orders/{orderNo}")
    public Result<OrderVO> getOrder(
        @AuthenticationPrincipal UserDetails user,
        @PathVariable String orderNo
    );
    
    @PostMapping("/alipay/create")
    public Result<PaymentFormVO> createAlipayForm(
        @AuthenticationPrincipal UserDetails user,
        @RequestBody @Valid CreateOrderRequest request
    );
    
    @PostMapping("/alipay/notify")
    public String handleAlipayNotify(@RequestParam Map<String, String> params);
    
    @GetMapping("/packages")
    public Result<List<QuotaPackageVO>> listQuotaPackages();
}

// 订单创建请求
public record CreateOrderRequest(
    @NotNull Long packageId,      // 套餐ID
    String returnUrl               // 支付成功后跳转URL
) {}

// 支付宝表单响应
public record PaymentFormVO(
    String orderNo,
    String formHtml               // 支付宝返回的表单HTML
) {}
```

#### 文献任务模块
```java
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    
    @PostMapping("/search")
    public Result<TaskVO> createSearchTask(
        @AuthenticationPrincipal UserDetails user,
        @RequestBody @Valid SearchTaskRequest request
    );
    
    @GetMapping("/{taskNo}")
    public Result<TaskVO> getTask(
        @AuthenticationPrincipal UserDetails user,
        @PathVariable String taskNo
    );
    
    @GetMapping("/list")
    public Result<PageResult<TaskVO>> listTasks(
        @AuthenticationPrincipal UserDetails user,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int size
    );
    
    @GetMapping("/{taskNo}/papers")
    public Result<PageResult<PaperVO>> getTaskPapers(
        @AuthenticationPrincipal UserDetails user,
        @PathVariable String taskNo,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int size
    );
    
    @GetMapping("/{taskNo}/download")
    public ResponseEntity<Resource> downloadResult(
        @AuthenticationPrincipal UserDetails user,
        @PathVariable String taskNo
    );
}

// 搜索任务请求
public record SearchTaskRequest(
    @NotBlank String source,           // wos / sciencedirect / semanticscholar
    @NotBlank String keyword,
    Integer yearFrom,                   // 起始年份
    Integer yearTo,                     // 结束年份
    List<String> quartiles,             // ["Q1", "Q2"]
    Double minImpactFactor,
    Boolean useAI,                      // 是否使用AI总结
    Integer maxResults                  // 最大结果数，默认50
) {}
```

### 4.3 核心 Service 接口定义

```java
public interface TaskService {
    /**
     * 创建搜索任务
     */
    TaskVO createTask(Long userId, SearchTaskRequest request);
    
    /**
     * 处理任务（异步执行）
     */
    void processTask(String taskNo);
    
    /**
     * 获取任务状态
     */
    TaskVO getTask(String taskNo);
    
    /**
     * 导出Excel
     */
    Resource exportToExcel(String taskNo);
}

public interface ScraperService {
    /**
     * 调用Node爬虫服务获取文献列表
     */
    List<ScraperPaperDTO> scrapePapers(String source, String keyword, Map<String, Object> filters);
}

public interface AIService {
    /**
     * 批量生成文献总结（支持系统API或用户自带Key）
     * 
     * @param userId 用户ID
     * @param papers 文献列表
     * @return 每篇文献的总结结果
     */
    List<PaperSummaryDTO> summarizeBatch(Long userId, List<Paper> papers);
    
    /**
     * 测试用户自定义AI配置
     */
    boolean testUserConfig(UserAIConfig config);
}

// 批量总结结果 DTO
public record PaperSummaryDTO(
    String title,
    String summary,           // 一句话核心贡献
    List<String> keyPoints,   // 要点列表
    String methodology,       // 研究方法
    String conclusion,        // 主要结论
    String researchFindings   // 研究成果
) {}

// 用户AI配置 DTO
public record UserAIConfig(
    String provider,          // openai/deepseek/claude/glm/custom
    String apiKey,            // 明文（仅传输，不存储）
    String baseUrl,           // 可选，自定义Base URL
    String model              // 模型名
) {}

public interface QuotaService {
    /**
     * 检查并扣除额度（BYOK用户不扣减）
     * @return true-扣除成功 false-额度不足
     */
    boolean deductQuota(Long userId, int amount, String reason, Long taskId);
    
    /**
     * 增加额度（充值、赠送）
     */
    void addQuota(Long userId, int amount, String reason, Long orderId);
    
    /**
     * 获取今日免费额度使用情况
     */
    FreeQuotaInfo getFreeQuotaInfo(Long userId);
    
    /**
     * 检查BYOK用户是否超出每日调用上限
     */
    boolean checkByokDailyLimit(Long userId);
}
```

---

## 5. 导出服务设计 (新增)

### 5.1 导出方式

| 方式 | 特点 | 适用场景 |
|------|------|----------|
| **飞书文档** | 格式美观、支持折叠/筛选、无需存储成本 | 首选推荐 |
| **Excel下载** | 传统格式、离线可用 | 需要本地文件时 |

### 5.2 Excel格式设计

```
列结构:
A: 序号
B: 标题（冻结列 + 超链接到原文）
C: AI核心总结（50字以内）
D: 研究方法
E: 研究结论
F: 研究成果
G: 关键词
H: 期刊
I: 分区（Q1/Q2/Q3/Q4）
J: 年份
K: 被引次数
L: 原文链接（超链接）

格式优化:
- 标题列冻结（滚动时始终可见）
- 自动换行 + 合适行高
- 长文本单元格hover显示完整内容
```

### 5.3 存储策略

| 导出方式 | 存储方案 | 有效期 |
|---------|----------|--------|
| 飞书文档 | 不保存URL（飞书云存储）或仅存文档ID | 永久（飞书） |
| Excel | MinIO/本地临时存储 | **7天后自动删除** |

### 5.4 飞书文档导出实现

```java
@Service
public class FeishuExportService {
    
    public String exportToFeishu(List<Paper> papers, String userOpenId) {
        // 1. 创建飞书表格文档
        String docTitle = "文献分析_" + LocalDate.now();
        
        // 2. 构建表格数据
        List<List<String>> tableData = new ArrayList<>();
        tableData.add(Arrays.asList("序号", "标题", "AI总结", "研究方法", "研究结论", 
            "研究成果", "关键词", "期刊", "分区", "年份", "被引"));
        
        for (int i = 0; i < papers.size(); i++) {
            Paper p = papers.get(i);
            tableData.add(Arrays.asList(
                String.valueOf(i + 1),
                p.getTitle(),
                p.getAiSummary(),
                p.getMethodology(),
                p.getConclusion(),
                p.getResearchFindings(),
                String.join(", ", p.getAiKeywords()),
                p.getJournal(),
                p.getQuartile(),
                String.valueOf(p.getPublishYear()),
                String.valueOf(p.getCitations())
            ));
        }
        
        // 3. 调用飞书API创建文档
        // 权限需申请: drive:drive, drive:file:write
        
        // 4. 返回文档链接
        return feishuDocUrl;
    }
}
```

### 5.5 Excel导出实现

```java
@Service
public class ExcelExportService {
    
    @Autowired
    private MinioClient minioClient;
    
    public String exportToExcel(List<Paper> papers, String taskNo) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("文献分析");
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {"序号", "标题", "AI总结", "研究方法", "研究结论", 
            "研究成果", "关键词", "期刊", "分区", "年份", "被引", "原文链接"};
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 冻结标题列（B列）
        sheet.createFreezePane(2, 1);
        
        // 填充数据
        CellStyle wrapStyle = createWrapStyle(workbook);
        for (int i = 0; i < papers.size(); i++) {
            Paper p = papers.get(i);
            Row row = sheet.createRow(i + 1);
            
            row.createCell(0).setCellValue(i + 1);
            
            // 标题 + 超链接
            Cell titleCell = row.createCell(1);
            titleCell.setCellValue(p.getTitle());
            Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            link.setAddress(p.getSourceUrl());
            titleCell.setHyperlink(link);
            
            // AI总结（自动换行）
            Cell summaryCell = row.createCell(2);
            summaryCell.setCellValue(p.getAiSummary());
            summaryCell.setCellStyle(wrapStyle);
            
            // 其他列...
            row.createCell(3).setCellValue(p.getMethodology());
            row.createCell(4).setCellValue(p.getConclusion());
            row.createCell(5).setCellValue(p.getResearchFindings());
            row.createCell(6).setCellValue(String.join(", ", p.getAiKeywords()));
            row.createCell(7).setCellValue(p.getJournal());
            row.createCell(8).setCellValue(p.getQuartile());
            row.createCell(9).setCellValue(p.getPublishYear());
            row.createCell(10).setCellValue(p.getCitations());
            
            // 原文链接
            Cell urlCell = row.createCell(11);
            urlCell.setCellValue("查看原文");
            Hyperlink urlLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            urlLink.setAddress(p.getSourceUrl());
            urlCell.setHyperlink(urlLink);
        }
        
        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        
        // 保存到MinIO，设置7天过期
        String fileName = "paperpilot_" + taskNo + "_" + System.currentTimeMillis() + ".xlsx";
        // ...
        
        return downloadUrl;
    }
}
```
  keyword: string;
  filters?: {
    yearFrom?: number;
    yearTo?: number;
    maxResults?: number;  // 默认50，最大200
  };
  cookies?: string;  // 用户机构账号的Cookie
}

interface ScrapeResponse {
  success: boolean;
  papers: PaperDTO[];
  totalCount: number;
  message?: string;
}

interface PaperDTO {
  doi?: string;
  title: string;
  authors: string[];
  abstract?: string;
  journal?: string;
  publishYear?: number;
  impactFactor?: number;
  quartile?: 'Q1' | 'Q2' | 'Q3' | 'Q4';
  citations?: number;
  pdfUrl?: string;
  sourceUrl: string;
}
```

### 5.3 WOS 爬虫核心逻辑（已改为前端实现）

**变更说明**: Node Scraper服务已移除，改为插件Content Script直接读取WOS页面。

```javascript
// content.ts - 插件内容脚本直接读取WOS页面
function extractPapersFromPage(): Paper[] {
  const papers: Paper[] = [];
  const rows = document.querySelectorAll('.search-results-content .search-result');
  
  rows.forEach((row, index) => {
    papers.push({
      id: index,
      title: row.querySelector('.title')?.textContent?.trim() || '',
      authors: Array.from(row.querySelectorAll('.author')).map(a => a.textContent.trim()),
      abstract: row.querySelector('.abstract')?.textContent?.trim() || '',
      journal: row.querySelector('.source')?.textContent?.trim() || '',
      publishYear: parseInt(row.querySelector('.year')?.textContent?.trim() || '0'),
      impactFactor: parseFloat(row.querySelector('.impact-factor')?.textContent?.trim() || '0'),
      quartile: row.querySelector('.quartile')?.textContent?.trim() as Quartile,
      citations: parseInt(row.querySelector('.citations')?.textContent?.trim() || '0'),
      sourceUrl: row.querySelector('.title a')?.getAttribute('href') || ''
    });
  });
  
  return papers;
}
```

**说明**: 直接读取当前WOS页面已显示的文献（默认50条），无需后端爬虫服务。

---

## 6. Chrome Extension 设计

### 6.1 项目结构
```
paperpilot-extension/
├── manifest.json              # Manifest V3
├── popup/                     # 弹出窗口
│   ├── popup.html
│   ├── popup.ts
│   ├── popup.css
│   └── components/
├── content/                   # 内容脚本（注入WOS/SD页面）
│   ├── content.ts
│   └── styles/
├── background/                # 后台Service Worker
│   └── background.ts
├── options/                   # 设置页面
│   ├── options.html
│   └── options.ts
├── shared/                    # 共享代码
│   ├── api.ts                 # 后端API封装
│   ├── storage.ts             # chrome.storage 封装
│   └── types.ts
├── assets/                    # 图标等资源
└── package.json
```

### 6.2 manifest.json
```json
{
  "manifest_version": 3,
  "name": "PaperPilot - 学术文献助手",
  "version": "1.0.0",
  "description": "智能检索WOS/SD文献，AI总结，一键导出Excel",
  "permissions": [
    "storage",
    "activeTab",
    "scripting"
  ],
  "host_permissions": [
    "https://www.webofscience.com/*",
    "https://www.sciencedirect.com/*",
    "https://api.paperpilot.com/*"
  ],
  "action": {
    "default_popup": "popup/popup.html",
    "default_icon": {
      "16": "assets/icon16.png",
      "48": "assets/icon48.png",
      "128": "assets/icon128.png"
    }
  },
  "background": {
    "service_worker": "background/background.ts",
    "type": "module"
  },
  "content_scripts": [
    {
      "matches": [
        "https://www.webofscience.com/*",
        "https://www.sciencedirect.com/*"
      ],
      "js": ["content/content.ts"],
      "css": ["content/styles/inject.css"]
    }
  ],
  "options_page": "options/options.html"
}
```

### 6.3 核心 TypeScript 类型
```typescript
// shared/types.ts

interface User {
  id: number;
  email: string;
  nickname: string;
  quotaBalance: number;
  isVip: boolean;
  vipExpireAt?: string;
}

interface SearchTask {
  taskNo: string;
  source: 'wos' | 'sciencedirect' | 'semanticscholar';
  keyword: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  totalCount: number;
  processedCount: number;
  resultFileUrl?: string;
  createdAt: string;
}

interface Paper {
  id?: number;               // 前端临时ID
  doi?: string;
  title: string;
  authors: string[];
  abstract?: string;
  journal?: string;
  publishYear?: number;
  impactFactor?: number;
  quartile?: 'Q1' | 'Q2' | 'Q3' | 'Q4';
  citations?: number;
  sourceUrl: string;
  // AI分析结果
  aiSummary?: string;        // 一句话核心总结
  aiKeywords?: string[];     // AI提取关键词
  methodology?: string;      // 研究方法
  conclusion?: string;       // 研究结论
  researchFindings?: string; // 研究成果
  selected?: boolean;        // 用户是否选中
}

interface UserAIConfig {
  provider: 'openai' | 'deepseek' | 'claude' | 'glm' | 'custom';
  apiKey: string;
  baseUrl?: string;          // 可选，默认用官方
  model: string;
  isDefault: boolean;        // 是否设为默认
}

interface ExportOptions {
  format: 'feishu' | 'excel';  // 导出方式
  selectedOnly: boolean;       // 仅导出选中项
}

interface SearchFilters {
  yearFrom?: number;
  yearTo?: number;
  quartiles?: ('Q1' | 'Q2' | 'Q3' | 'Q4')[];
  minImpactFactor?: number;
  useAI: boolean;
  maxResults: number;
}
```

### 6.4 API 封装
```typescript
// shared/api.ts

const API_BASE = 'https://api.paperpilot.com/api/v1';

class PaperPilotAPI {
  private async request<T>(
    endpoint: string,
    options?: RequestInit
  ): Promise<T> {
    const token = await this.getToken();
    
    const response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        ...options?.headers
      }
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Request failed');
    }
    
    return response.json();
  }
  
  // 认证
  async login(email: string, password: string): Promise<AuthResponse> {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  }
  
  // 用户信息
  async getProfile(): Promise<User> {
    return this.request('/user/profile');
  }
  
  async getQuota(): Promise<{ balance: number; freeQuotaUsed: number; freeQuotaTotal: number }> {
    return this.request('/user/quota');
  }
  
  // 任务
  async createTask(request: CreateTaskRequest): Promise<SearchTask> {
    return this.request('/tasks/search', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  }
  
  async getTask(taskNo: string): Promise<SearchTask> {
    return this.request(`/tasks/${taskNo}`);
  }
  
  async getTaskPapers(taskNo: string, page: number = 1): Promise<PaginatedResult<Paper>> {
    return this.request(`/tasks/${taskNo}/papers?page=${page}`);
  }
  
  // 支付
  async createOrder(packageId: number): Promise<Order> {
    return this.request('/payment/orders', {
      method: 'POST',
      body: JSON.stringify({ packageId })
    });
  }
  
  async createAlipayPayment(orderNo: string): Promise<{ formHtml: string }> {
    return this.request('/payment/alipay/create', {
      method: 'POST',
      body: JSON.stringify({ orderNo })
    });
  }
  
  // ========== 用户AI配置 (BYOK) ==========
  async getUserAIConfigs(): Promise<UserAIConfig[]> {
    return this.request('/user/ai-configs');
  }
  
  async saveUserAIConfig(config: UserAIConfig): Promise<void> {
    return this.request('/user/ai-configs', {
      method: 'POST',
      body: JSON.stringify(config)
    });
  }
  
  async testUserAIConfig(config: UserAIConfig): Promise<{ success: boolean; message: string }> {
    return this.request('/user/ai-configs/test', {
      method: 'POST',
      body: JSON.stringify(config)
    });
  }
  
  async deleteUserAIConfig(provider: string): Promise<void> {
    return this.request(`/user/ai-configs/${provider}`, {
      method: 'DELETE'
    });
  }
  
  // ========== AI分析 ==========
  async analyzePapers(papers: Paper[], useUserConfig?: boolean): Promise<Paper[]> {
    return this.request('/ai/analyze-batch', {
      method: 'POST',
      body: JSON.stringify({ 
        papers: papers.filter(p => p.selected !== false),  // 默认全选
        useUserConfig 
      })
    });
  }
  
  // ========== 导出 ==========
  async exportToFeishu(papers: Paper[]): Promise<{ docUrl: string }> {
    return this.request('/export/feishu', {
      method: 'POST',
      body: JSON.stringify({ papers: papers.filter(p => p.selected) })
    });
  }
  
  async exportToExcel(papers: Paper[]): Promise<{ downloadUrl: string; expiresAt: string }> {
    return this.request('/export/excel', {
      method: 'POST',
      body: JSON.stringify({ papers: papers.filter(p => p.selected) })
    });
  }
  
  private async getToken(): Promise<string> {
    const result = await chrome.storage.local.get('accessToken');
    return result.accessToken;
  }
}

export const api = new PaperPilotAPI();
```

### 6.5 Popup 页面结构
```html
<!-- popup/popup.html -->
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <link rel="stylesheet" href="popup.css">
</head>
<body>
  <div id="app">
    <!-- 未登录状态 -->
    <div id="login-section" class="hidden">
      <h1>PaperPilot</h1>
      <form id="login-form">
        <input type="email" id="email" placeholder="邮箱" required>
        <input type="password" id="password" placeholder="密码" required>
        <button type="submit">登录</button>
      </form>
      <p>还没有账号？<a href="#" id="show-register">立即注册</a></p>
    </div>
    
    <!-- 已登录状态 -->
    <div id="main-section" class="hidden">
      <header>
        <span id="user-email"></span>
        <span id="quota-badge">剩余: <strong id="quota-count">0</strong> 次</span>
      </header>
      
      <!-- 步骤1: 文献选择 -->
      <div id="paper-selection" class="panel">
        <h3>📚 选择要分析的文献 (<span id="selected-count">0</span>/<span id="total-count">0</span>)</h3>
        <div class="toolbar">
          <button id="select-all">全选</button>
          <button id="select-none">取消全选</button>
          <button id="select-q1">仅选Q1</button>
        </div>
        <div id="paper-list" class="paper-list">
          <!-- 动态加载文献列表 -->
        </div>
        <button id="goto-ai-config" class="secondary">下一步: 配置AI →</button>
      </div>
      
      <!-- 步骤2: AI配置 -->
      <div id="ai-config" class="panel hidden">
        <h3>🤖 AI分析配置</h3>
        <div class="config-section">
          <label>
            <input type="radio" name="ai-source" value="system" checked>
            使用系统AI (消耗额度)
          </label>
          <label>
            <input type="radio" name="ai-source" value="user">
            使用我的API Key (免费)
          </label>
        </div>
        <div id="user-ai-config" class="hidden">
          <select id="saved-configs">
            <option value="">选择已保存的配置...</option>
          </select>
          <button id="add-ai-config" class="text-btn">+ 添加新配置</button>
        </div>
        <div class="actions">
          <button id="back-to-selection" class="secondary">← 返回</button>
          <button id="start-analyze" class="primary">开始AI分析</button>
        </div>
      </div>
      
      <!-- 步骤3: 分析结果 & 导出 -->
      <div id="analysis-result" class="panel hidden">
        <h3>✅ 分析完成</h3>
        <div id="result-preview" class="preview">
          <!-- 预览前3条结果 -->
        </div>
        <div class="export-section">
          <h4>导出方式</h4>
          <button id="export-feishu" class="primary">📄 导出到飞书文档</button>
          <button id="export-excel" class="secondary">📊 下载Excel</button>
        </div>
      </div>
      
      <!-- 设置入口 -->
      <div class="footer-actions">
        <button id="open-settings">⚙️ 设置</button>
        <button id="logout-btn">退出</button>
      </div>
    </div>
  </div>
  
  <script src="popup.ts" type="module"></script>
</body>
</html>
      <div class="search-panel">
        <select id="source-select">
          <option value="wos">Web of Science</option>
          <option value="sciencedirect">ScienceDirect</option>
          <option value="semanticscholar">Semantic Scholar</option>
        </select>
        
        <input type="text" id="keyword-input" placeholder="输入关键词...">
        
        <div class="filters">
          <div class="filter-row">
            <label>年份:</label>
            <input type="number" id="year-from" placeholder="起始">
            <span>-</span>
            <input type="number" id="year-to" placeholder="结束">
          </div>
          
          <div class="filter-row">
            <label>分区:</label>
            <label><input type="checkbox" value="Q1" checked> Q1</label>
            <label><input type="checkbox" value="Q2"> Q2</label>
            <label><input type="checkbox" value="Q3"> Q3</label>
            <label><input type="checkbox" value="Q4"> Q4</label>
          </div>
          
          <div class="filter-row">
            <label>
              <input type="checkbox" id="use-ai" checked>
              使用 AI 总结
            </label>
          </div>
        </div>
        
        <button id="search-btn" class="primary">开始搜索</button>
      </div>
      
      <!-- 任务列表 -->
      <div class="tasks-panel">
        <h3>最近任务</h3>
        <ul id="task-list"></ul>
      </div>
      
      <!-- 充值入口 -->
      <div class="actions">
        <button id="recharge-btn">充值额度</button>
        <button id="logout-btn">退出登录</button>
      </div>
    </div>
  </div>
  
  <script src="popup.ts" type="module"></script>
</body>
</html>
```

---

## 7. 配置与部署

### 7.1 Java 配置 (application.yml)
```yaml
server:
  port: 8080

spring:
  application:
    name: paperpilot-backend
  
  datasource:
    url: jdbc:mysql://localhost:3306/paperpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:paperpilot}
    password: ${DB_PASSWORD:your_password}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    lettuce:
      pool:
        max-active: 8

# JWT 配置
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-here-min-32-chars}
  access-token-expiration: 86400000      # 1天 (毫秒)
  refresh-token-expiration: 604800000    # 7天 (毫秒)

# 支付宝配置（沙箱环境）
alipay:
  app-id: ${ALIPAY_APP_ID:}
  private-key: ${ALIPAY_PRIVATE_KEY:}
  alipay-public-key: ${ALIPAY_PUBLIC_KEY:}
  gateway-url: https://openapi.alipaydev.com/gateway.do  # 沙箱
  # gateway-url: https://openapi.alipay.com/gateway.do   # 正式
  notify-url: ${APP_URL}/api/v1/payment/alipay/notify
  return-url: ${APP_URL}/payment/success

# Node Scraper 服务地址
scraper:
  base-url: ${SCRAPER_URL:http://localhost:3000}
  timeout: 120000  # 2分钟

# AI 服务配置
ai:
  provider: ${AI_PROVIDER:deepseek}  # deepseek / openai / claude / glm
  api-key: ${AI_API_KEY:}
  model: ${AI_MODEL:deepseek-chat}
  base-url: ${AI_BASE_URL:https://api.deepseek.com/v1}  # DeepSeek默认
  
  # 批量处理配置
  batch:
    max-tokens-per-batch: 6000     # 每批最大输入token数（为输出留空间）
    max-concurrency: 3             # 最大并发请求数
    timeout-seconds: 60            # 单批处理超时
    retry-attempts: 2              # 失败重试次数
    fallback-to-single: true       # 批量失败时是否降级为单篇处理
  
  # 单篇配置
  max-tokens: 2000
  temperature: 0.3

# 免费额度配置
free-quota:
  daily-limit: 3
```

### 7.2 Node Scraper 配置
```typescript
// src/config/index.ts
export const config = {
  port: process.env.PORT || 3000,
  
  browser: {
    headless: process.env.NODE_ENV === 'production',
    slowMo: parseInt(process.env.SLOW_MO || '0'),
    timeout: 120000,
    maxPages: 5,  // 浏览器池最大页签数
  },
  
  scraping: {
    defaultMaxResults: 50,
    maxMaxResults: 200,
    delayBetweenRequests: [2000, 5000],  // 随机延迟范围 (ms)
  },
  
  // 反爬配置
  antiDetection: {
    rotateUserAgent: true,
    randomViewport: true,
    // ...
  }
};
```

### 7.3 Docker Compose 部署
```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: paperpilot-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: paperpilot
      MYSQL_USER: paperpilot
      MYSQL_PASSWORD: paperpilot_pass
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "3306:3306"
    command: --default-authentication-plugin=mysql_native_password

  redis:
    image: redis:7-alpine
    container_name: paperpilot-redis
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"

  backend:
    build: ./paperpilot-backend
    container_name: paperpilot-backend
    environment:
      - DB_USERNAME=paperpilot
      - DB_PASSWORD=paperpilot_pass
      - REDIS_HOST=redis
      - JWT_SECRET=your-secret-key-change-in-production
      - ENCRYPTION_KEY=${ENCRYPTION_KEY}  # BYOK API Key加密密钥
      - AI_API_KEY=${AI_API_KEY}          # 系统默认AI Key
      - AI_PROVIDER=${AI_PROVIDER:-deepseek}
      - AI_MODEL=${AI_MODEL:-deepseek-chat}
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis
    restart: unless-stopped

  minio:
    image: minio/minio:latest
    container_name: paperpilot-minio
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

  nginx:
    image: nginx:alpine
    container_name: paperpilot-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  mysql_data:
  redis_data:
  minio_data:
```

### 7.4 加密配置 (BYOK功能)

```yaml
# application.yml
encryption:
  key: ${ENCRYPTION_KEY}  # 32字节AES密钥，环境变量注入
  algorithm: AES/GCM/NoPadding
```

**Java加密工具类**:
```java
@Component
public class AESUtil {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    @Value("${encryption.key}")
    private String masterKey;
    
    public String encrypt(String plainText) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(
            masterKey.getBytes(StandardCharsets.UTF_8), "AES"
        );
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encrypted.length);
        byteBuffer.put(iv);
        byteBuffer.put(encrypted);
        
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }
    
    public String decrypt(String encryptedText) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(
            masterKey.getBytes(StandardCharsets.UTF_8), "AES"
        );
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        byte[] decrypted = cipher.doFinal(cipherText);
        
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
```

---

## 7. 核心技术方案专题

### 7.1 WOS 数据无损抓取方案 (Request Interception)

由于 WOS 采用混淆类名且数据动态加载，本系统放弃 DOM 解析，采用 **Fetch Hook** 方案。

#### 7.1.1 核心劫持脚本 (inject.js)
```javascript
/**
 * 运行在 World: MAIN 环境
 * 负责拦截并分发原始数据报文
 */
(function() {
    const originalFetch = window.fetch;
    window.fetch = async (...args) => {
        const response = await originalFetch(...args);
        const url = args[0];
        
        // 目标接口: WOS 核心搜索结果 API
        if (typeof url === 'string' && url.includes('/api/wosnx/core/runQuerySearch')) {
            const clone = response.clone();
            clone.text().then(text => {
                // 转发给 Content Script
                window.postMessage({
                    source: 'paper-pilot-hook',
                    type: 'WOS_BATCH_DATA',
                    data: text
                }, "*");
            }).catch(err => console.error('[PaperPilot] Data clone failed:', err));
        }
        return response;
    };
})();
```

#### 7.1.2 数据链路流程
1. **注入**: `manifest.json` 配置 `content_scripts` 并在页面加载时通过 `document.createElement('script')` 注入上述代码。
2. **捕获**: 用户在 WOS 翻页或检索时，`inject.js` 自动捕获响应流。
3. **分发**: 通过 `postMessage` 跨出沙箱，由 `content-script.js` 接收。
4. **解析**: 插件端调用 `ndjson-parser` 解析多行数据，提取 DOI、标题、摘要等。
5. **缓存**: 插件将解析后的列表暂存于 `chrome.storage.local`，供用户在 Popup 界面勾选分析。

---

## 8. 开发计划与 MVP 功能清单

### Phase 1: MVP (2-3周)
- [ ] Java 基础框架搭建 + 数据库
- [ ] 用户注册/登录/JWT
- [ ] **AES加密工具类** (BYOK API Key加密存储)
- [ ] **用户AI配置管理** (BYOK功能)
- [ ] **AI服务双模式** (系统API + 用户自带Key)
- [ ] Chrome 插件基础结构
- [ ] **Content Script读取WOS页面**
- [ ] **文献选择界面** (勾选/全选/筛选)
- [ ] **AI批量分析** (研究方法/结论/成果提取)
- [ ] **导出功能**: 飞书文档 + Excel双方案
- [ ] MinIO临时存储 (7天自动清理)

### Phase 2: 商业化 (1-2周)
- [ ] 支付宝沙箱接入
- [ ] 额度购买/充值
- [ ] 订单管理
- [ ] BYOK用户限流 (每日50次上限)
- [ ] API Key 管理

### Phase 3: 高级功能 (未来扩展)
- [ ] Node爬虫服务 (深度抓取多页)
- [ ] 订阅会员系统
- [ ] 数据统计后台
- [ ] 历史记录持久化

### Phase 4: 优化与上线
- [ ] 支付宝正式环境切换
- [ ] 性能优化
- [ ] 监控/日志
- [ ] 用户反馈收集

---

## 9. 关键设计决策说明

### 9.1 为什么用 API Key + JWT 双认证？
- **JWT**: 用于浏览器端用户会话，有有效期
- **API Key**: 用于插件长期认证，可撤销，适合插件场景

### 9.2 为什么砍掉Node爬虫服务？
- **MVP优先**: 纯前端读取当前页更简单，快速验证需求
- **无反爬风险**: 直接读取WOS页面不会触发风控
- **架构简化**: 减少一个服务，部署维护更容易
- **扩展预留**: 未来需要多页抓取时，可再添加Node服务

### 9.3 为什么支持BYOK（用户自带API Key）？
- **降低门槛**: 有Key的用户免费用，更容易推广
- **隐私保护**: 敏感数据不经过第三方AI（可选）
- **成本分摊**: 系统只承担免费额度用户的AI成本
- **灵活配置**: 支持OpenAI/DeepSeek/Claude等多种模型

### 9.4 为什么双导出方案（飞书+Excel）？
- **飞书优势**: 格式美观、无需存储成本、支持协作
- **Excel优势**: 传统格式、离线可用、科研工作者熟悉
- **用户自选**: 根据使用场景灵活选择

### 9.5 免费额度怎么实现？
- Redis 计数器，每日重置
- **BYOK用户**: 限流防刷（每日50次），但不扣费
- **系统AI用户**: 按 user_id 限制免费额度

### 9.6 AI Key加密方案
- **AES-256-GCM**: 对称加密，速度快，Java原生支持
- **环境变量注入**: `ENCRYPTION_KEY`不写死在代码
- **仅服务端解密**: 数据库只存密文，解密只在内存进行

---

## 11. AI成本控制与缓存策略

AI调用是PaperPilot最大的运营成本，必须建立完善的控制体系。

### 11.1 用户分层与限额策略

#### 用户类型分类
```sql
-- users表扩展字段
ALTER TABLE users ADD COLUMN ai_provider_type VARCHAR(20) DEFAULT 'system' COMMENT 'system|byok';
ALTER TABLE users ADD COLUMN daily_ai_limit INT DEFAULT 10 COMMENT '每日AI调用上限';
ALTER TABLE users ADD COLUMN total_ai_used INT DEFAULT 0 COMMENT '累计使用量';
ALTER TABLE users ADD COLUMN last_ai_reset DATETIME COMMENT '上次重置时间';
```

#### 限额层级配置
```yaml
# application.yml
ai:
  quota:
    free-user:
      daily-limit: 10           # 免费用户每日10次
      tokens-per-day: 10000     # 每日总token限制
      requests-per-minute: 1    # 每分钟请求限制
    paid-user:
      daily-limit: 50           # 付费用户每日50次
      tokens-per-day: 100000    # 每日总token限制  
      requests-per-minute: 5    # 每分钟请求限制
    byok-user:
      daily-limit: 100          # BYOK用户防刷上限
      tokens-per-day: 500000    # 每日总token限制
      requests-per-minute: 10   # 每分钟请求限制
```

### 11.2 Redis缓存策略（核心优化）

#### 缓存键设计
```
# 用户额度计数器（滑动窗口）
ai:limit:{user_id}:{date} -> count (TTL: 24h)

# 防刷限流（按分钟）
ai:rate:{user_id}:{minute} -> count (TTL: 60s)

# 文献摘要缓存（节省AI调用）
ai:cache:abstract:{md5_hash} -> {
    "title_zh": "...",
    "abstract_zh": "...",
    "background": "...",
    "purpose": "...",
    "method": "...",
    "result": "...",
    "conclusion": "...",
    "innovation": "..."
} (TTL: 30天)

# 期刊影响因子缓存（公共数据）
cache:if:{issn} -> {"impact_factor": 5.2, "quartile": "Q1"} (TTL: 7天)
```

#### 缓存命中流程
```java
@Service
public class AIServiceImpl implements AIService {
    
    public PaperSummaryDTO analyzePaper(Long userId, Paper paper) {
        // 1. 检查用户额度
        if (!checkQuota(userId)) {
            throw new QuotaExceededException("每日额度已用完");
        }
        
        // 2. 防刷限流
        if (!checkRateLimit(userId)) {
            throw new RateLimitException("请求过于频繁");
        }
        
        // 3. 缓存查找（摘要哈希）
        String hash = DigestUtils.md5Hex(paper.getAbstract());
        String cacheKey = "ai:cache:abstract:" + hash;
        PaperSummaryDTO cached = (PaperSummaryDTO) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            // 记录缓存命中（不扣额度）
            logCacheHit(userId);
            return cached;
        }
        
        // 4. 调用AI API
        PaperSummaryDTO result = callLLM(paper);
        
        // 5. 缓存结果
        redisTemplate.opsForValue().set(cacheKey, result, 30, TimeUnit.DAYS);
        
        // 6. 扣除额度
        deductQuota(userId, 1);
        
        return result;
    }
}
```

### 11.3 批量处理与智能去重

```java
public List<PaperSummaryDTO> summarizeBatch(Long userId, List<Paper> papers) {
    // 步骤1: 去重（同摘要的文献）
    Map<String, List<Paper>> groupedByHash = papers.stream()
        .collect(Collectors.groupingBy(p -> DigestUtils.md5Hex(p.getAbstract())));
    
    // 步骤2: 批量缓存查找
    List<PaperSummaryDTO> results = new ArrayList<>();
    List<Paper> toProcess = new ArrayList<>();
    
    for (Map.Entry<String, List<Paper>> entry : groupedByHash.entrySet()) {
        String cacheKey = "ai:cache:abstract:" + entry.getKey();
        PaperSummaryDTO cached = (PaperSummaryDTO) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            // 缓存命中，为所有相同摘要的文献复用结果
            for (Paper p : entry.getValue()) {
                results.add(cached);
            }
            logCacheHit(userId);
        } else {
            // 需要处理（只取第一篇，其他复用）
            toProcess.add(entry.getValue().get(0));
        }
    }
    
    // 步骤3: 批量调用AI（优化token使用）
    List<PaperSummaryDTO> aiResults = batchCallLLM(toProcess);
    
    // 步骤4: 批量缓存
    for (int i = 0; i < toProcess.size(); i++) {
        String hash = DigestUtils.md5Hex(toProcess.get(i).getAbstract());
        String cacheKey = "ai:cache:abstract:" + hash;
        redisTemplate.opsForValue().set(cacheKey, aiResults.get(i), 30, TimeUnit.DAYS);
    }
    
    return results;
}
```

### 11.4 成本监控与告警

```java
@Component
public class AICostMonitor {
    
    // 各AI供应商的成本（元/1000 tokens）
    private static final Map<String, Double> COST_PER_1K = Map.of(
        "deepseek", 0.001,      // DeepSeek-V3: ¥0.001/1K tokens
        "openai", 0.01,         // GPT-4: ¥0.01/1K tokens
        "claude", 0.008,        // Claude-3: ¥0.008/1K tokens
        "kimi", 0.002           // Kimi: ¥0.002/1K tokens
    );
    
    // 每日成本汇总与告警
    @Scheduled(cron = "0 0 9 * * ?") // 每天9点执行
    public void dailyCostReport() {
        String date = LocalDate.now().minusDays(1).toString();
        List<AILog> logs = aiLogMapper.selectByDate(date);
        
        Map<String, Double> costByProvider = new HashMap<>();
        for (AILog log : logs) {
            double cost = calculateCost(log.getProvider(), log.getInputTokens(), log.getOutputTokens());
            costByProvider.merge(log.getProvider(), cost, Double::sum);
        }
        
        double totalCost = costByProvider.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalCost > 100) { // 每日成本超过100元触发告警
            sendAlert("AI成本告警", String.format("昨日AI成本: ¥%.2f", totalCost));
        }
    }
}
```

### 11.5 防刷与安全措施

```java
public class AntiAbuseFilter {
    
    public boolean checkRequest(Long userId, String ip) {
        // 1. IP限制（同一IP每日最多50个用户）
        String ipKey = "abuse:ip:" + ip + ":" + LocalDate.now();
        Long ipCount = redisTemplate.opsForValue().increment(ipKey, 1);
        if (ipCount > 50) {
            log.warn("IP {} 疑似刷量", ip);
            return false;
        }
        
        // 2. 行为模式检测（短时间内大量相似请求）
        String patternKey = "abuse:pattern:" + userId;
        String requestHash = hashRequest(request);
        redisTemplate.opsForList().leftPush(patternKey, requestHash);
        redisTemplate.opsForList().trim(patternKey, 0, 9); // 保留最近10次
        
        // 检查重复率
        List<Object> recentRequests = redisTemplate.opsForList().range(patternKey, 0, -1);
        long duplicateCount = recentRequests.stream()
            .filter(h -> h.equals(requestHash))
            .count();
        
        return duplicateCount <= 3; // 10次内有4次相同请求则拒绝
    }
}
```

---

## 12. 服务器部署与资源配置方案

基于低成本验证MVP的原则，推荐使用两台2C2G服务器部署。

### 12.1 服务器分配策略

#### 服务器A（2C2G）：轻量服务
```
服务: Nginx + Redis + 监控
内存分配:
- Nginx: 100MB
- Redis: 1GB (maxmemory=800mb)  # 重点缓存服务
- Node Exporter + 监控: 100MB
- 系统: 800MB
总计: 2GB

优势:
- Redis独享1GB内存，缓存命中率高
- Nginx轻量，不占资源
- 稳定可靠
```

#### 服务器B（2C2G）：重量服务
```
服务: Java + MySQL（同机部署）+ MinIO（本地磁盘）
内存分配:
- Java: 1GB (-Xmx768m)
- MySQL: 768MB (innodb_buffer_pool_size=512M)
- 系统: 232MB
总计: 2GB

优势:
- Java和MySQL在同一台，数据库访问快
- 内存相对集中，没有网络开销
- MinIO使用本地磁盘，节省内存
```

### 12.2 网络架构
```
┌─────────────────┐      ┌─────────────────┐
│  服务器A (2C2G)  │      │  服务器B (2C2G)  │
│  - Nginx        │◄────►│  - Java Spring  │
│  - Redis        │      │  - MySQL        │
│  - 监控         │      │  - MinIO        │
└─────────────────┘      └─────────────────┘
        ▲                         ▲
        │                         │
        └─────────────────────────┘
              客户端请求
```

### 12.3 性能优化配置

#### MySQL配置 (my.cnf)
```ini
[mysqld]
# 内存配置（512MB缓冲池）
innodb_buffer_pool_size = 512M
innodb_log_file_size = 64M
key_buffer_size = 32M
max_connections = 30

# 性能优化
innodb_flush_log_at_trx_commit = 2  # 牺牲一点持久性换性能
innodb_flush_method = O_DIRECT
innodb_file_per_table = ON

# 查询优化
query_cache_type = 0  # 禁用查询缓存（内存不够）
```

#### Java应用配置
```yaml
# application.yml
server:
  tomcat:
    max-threads: 20  # 减少线程数
    min-spare-threads: 5

spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 连接池减小
      minimum-idle: 3
      
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# JVM参数
JAVA_OPTS: "-Xmx768m -Xms256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### Redis配置 (redis.conf)
```ini
maxmemory 800mb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
requirepass your_strong_password  # 必须设置密码！
```

### 12.4 部署步骤

#### 服务器A部署
```bash
# 1. 安装Redis
apt install redis-server
# 配置: /etc/redis/redis.conf
# maxmemory 800mb
# maxmemory-policy allkeys-lru
# requirepass your_password

# 2. 安装Nginx
apt install nginx
# 配置反向代理到服务器B:8080

# 3. 防火墙配置
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 6379/tcp  # Redis（设置强密码！）
```

#### 服务器B部署
```bash
# 1. 安装MySQL 8.0
apt install mysql-server
# 配置my.cnf（内存优化版）

# 2. 部署Java应用
# 上传jar包，配置systemd服务

# 3. 创建数据库
mysql -e "CREATE DATABASE paperpilot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 4. 导入表结构
mysql paperpilot < init.sql
```

### 12.5 监控指标与告警

#### 关键监控指标
```bash
# 服务器A监控
redis-cli info memory | grep used_memory_human  # Redis内存使用率
netstat -an | grep :80 | wc -l                  # Nginx连接数
df -h | grep /dev/vda                           # 磁盘空间

# 服务器B监控
mysql -e "SHOW GLOBAL STATUS LIKE 'Questions';" # MySQL查询数
jstat -gc <pid> 1000                            # Java GC监控
uptime                                          # 系统负载
```

#### 扩容触发条件
```
如果以下任一条件持续30分钟:
1. 内存使用率 > 85%
2. CPU使用率 > 80%
3. 数据库连接数 > 25（max_connections=30）
4. 用户投诉响应慢

则考虑:
1. 升级服务器B到4C8G（主要压力在Java+MySQL）
2. 或者增加第三台服务器专门跑MySQL
```

### 12.6 承载能力估算

#### 乐观估计（性能良好）
```
用户数: 30-50个注册用户
并发: 5-10个同时在线
文献处理: 每次最多50篇
每日处理量: 300-500篇
AI成本: ¥5-10/日（假设缓存命中率60%）
```

#### 保守估计（最差情况）
```
用户数: 20个
并发: 3-5个
文献处理: 每次最多20篇
每日处理量: 100-200篇
AI成本: ¥2-5/日
```

### 12.7 成本效益分析

#### 第一年成本
```
服务器A: 已付费（沉没成本）
服务器B: ¥100/年（优惠套餐）
域名: ¥50/年（可选）
总计: ¥150/年 ≈ ¥12.5/月
```

#### 收益潜力
```
如果验证成功:
- 100个付费用户
- 每个用户每月消费¥10
月收入: ¥1000
年收入: ¥12000
投入产出比: 12000 / 150 = 80倍
```

### 12.8 风险应对方案

#### 风险1：MySQL内存不足
```
症状: 查询变慢，频繁磁盘IO
应对: 
1. 优化SQL，添加合适索引
2. 定期清理历史数据（保留30天）
3. 热点数据应用层缓存
```

#### 风险2：Java OOM
```
症状: 应用崩溃
应对:
1. 启用-XX:+HeapDumpOnOutOfMemoryError分析
2. 限制文献批量处理数量（最多20篇/次）
3. 使用流式处理Excel（SXSSFWorkbook）
```

#### 风险3：Redis成为瓶颈
```
症状: 缓存响应慢
应对:
1. 监控Redis内存使用率
2. 调整淘汰策略为allkeys-lru
3. 热点数据本地缓存（Caffeine二级缓存）
```

---

## 13. 风险提示

1. **法律风险**: 插件读取WOS页面需遵守使用条款，建议仅用于个人科研用途
2. **API Key安全**: 用户Key必须加密存储，泄露需立即撤销
3. **支付合规**: 正式上线需企业资质，个人阶段用第三方聚合支付过渡
4. **AI成本**: 系统承担免费用户成本，需做好额度控制和成本核算
5. **飞书权限**: 需申请云文档权限 (`drive:drive`, `drive:file:write`)
6. **服务器性能**: 2C2G×2台配置为最低配置，用户量增长需及时扩容
7. **缓存策略**: 依赖Redis缓存控制AI成本，必须确保Redis稳定运行

---

**文档结束**

生成指令示例:
- "生成 Java Backend 的完整代码，包括 entity、mapper、service、controller、BYOK配置管理"
- "生成 Chrome Extension 的完整代码，包含 popup（文献选择）、content script（WOS页面读取）、background"
