# PaperPilot 后端服务

学术文献助手后端服务，基于 Spring Boot 3.x + Java 17 开发。

## 技术栈

- Spring Boot 3.2.0
- Java 17
- MySQL 8.0
- Redis 7.x
- MyBatis-Plus
- Spring Security + JWT
- 本地文件存储（导出Excel，7天过期自动清理）
- Apache POI (Excel导出)

## 项目结构

```
paperpilot-backend/
├── src/main/java/com/paperpilot/
│   ├── PaperPilotApplication.java
│   ├── config/           # 配置类
│   ├── controller/       # 控制器层
│   ├── service/          # 服务层
│   ├── mapper/           # MyBatis-Plus Mapper
│   ├── entity/           # 实体类
│   ├── dto/              # 数据传输对象
│   ├── security/         # JWT安全相关
│   ├── util/             # 工具类
│   ├── exception/        # 异常处理
│   └── enums/            # 枚举
├── src/main/resources/
│   ├── application.yml   # 主配置
│   ├── application-dev.yml
│   └── db/init.sql       # 数据库初始化脚本
├── docker-compose.yml    # 本地开发环境
└── pom.xml
```

## 快速开始

### 1. 启动依赖服务

```bash
cd paperpilot-backend
docker-compose up -d
```

这会启动 MySQL、Redis。

### 2. 配置环境变量

在 `application-dev.yml` 中配置数据库连接和 API Key。

关键配置项：
- `jwt.secret`: JWT 签名密钥（至少32字符）
- `encryption.key`: AES 加密密钥（32字节）
- `ai.api-key`: 系统默认 AI API Key
- `feishu.app-id` / `feishu.app-secret`: 飞书应用凭证

### 3. 运行项目

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package
java -jar target/paperpilot-backend-1.0.0.jar
```

## 主要功能模块

### 1. 用户认证
- 邮箱注册/登录
- JWT Token 认证
- Token 刷新

### 2. 额度管理
- 付费额度管理
- 每日免费额度（默认3次）
- Redis 计数器实现

### 3. AI 配置管理 (BYOK)
- 用户自带 API Key
- AES-GCM 加密存储
- 支持多提供商（OpenAI/DeepSeek/Claude等）

### 4. AI 文献分析
- 批量文献分析
- 双模式支持（系统AI/BYOK）
- BYOK 日调用限流（默认50次）

### 5. 导出服务
- Excel 导出（本地文件系统存储，7天过期，流式下载）
- 飞书文档导出

## API 接口

### 认证接口
- `POST /api/v1/auth/register` - 注册
- `POST /api/v1/auth/login` - 登录
- `POST /api/v1/auth/refresh` - 刷新Token

### 用户接口
- `GET /api/v1/user/profile` - 获取用户信息
- `GET /api/v1/user/quota` - 获取额度信息

### AI 配置接口
- `GET /api/v1/user/ai-configs` - 获取用户AI配置
- `POST /api/v1/user/ai-configs` - 保存AI配置
- `DELETE /api/v1/user/ai-configs/{provider}` - 删除配置
- `POST /api/v1/user/ai-configs/test` - 测试配置

### AI 分析接口
- `POST /api/v1/ai/analyze-batch` - 批量分析文献

### 导出接口
- `POST /api/v1/export/feishu` - 导出到飞书文档
- `POST /api/v1/export/excel` - 导出Excel（返回fileId）
- `GET /api/v1/export/download/{fileId}` - 下载Excel文件（流式传输）

## 安全特性

- JWT Token 认证
- AES-GCM 加密存储用户 API Key
- BCrypt 密码加密
- CORS 跨域支持

## 默认账号

- 邮箱: admin@paperpilot.com
- 密码: admin123

## 开发计划

### 已完成
- [x] 项目基础架构
- [x] 用户认证模块
- [x] 额度管理模块
- [x] AI 配置管理 (BYOK)
- [x] AI 文献分析服务
- [x] 导出服务 (Excel + 飞书)
- [x] **AI成本监控与缓存策略** (V1.1)
- [x] **限流与防刷机制** (V1.1)
- [x] **文献摘要缓存** (V1.1)
- [x] **AI调用日志记录** (V1.1)

### ### 待开发
- [ ] 支付宝支付集成
- [ ] 订单管理
- [ ] 额度充值
- [ ] 管理后台接口

## 开发环境联调指南 (IDEA)

### 1. 准备工作

**启动基础服务**:
```bash
cd paperpilot-backend
docker-compose up -d
```

这会启动 MySQL 和 Redis。

### 2. IDEA 配置

**导入项目**:
1. File → Open → 选择 `paperpilot-backend` 目录
2. IDEA 自动识别 Maven 项目，等待依赖下载完成

**配置文件** (`src/main/resources/application-dev.yml`):
```yaml
# 复制 application.yml 为 application-dev.yml 并修改

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paperpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: paperpilot
    password: paperpilot_pass

  redis:
    host: localhost
    port: 6379

  # 邮箱配置（使用QQ邮箱示例）
  mail:
    host: smtp.qq.com
    port: 587
    username: your_qq@qq.com
    password: your_auth_code  # QQ邮箱授权码，不是登录密码

jwt:
  secret: your-development-secret-key-min-32-characters-long

encryption:
  key: your-32-byte-encryption-key!

ai:
  api-key: your-deepseek-api-key
```

**获取邮箱授权码**:
- QQ邮箱: 设置 → 账户 → 开启SMTP服务 → 获取授权码
- 163邮箱: 设置 → POP3/SMTP → 开启并获取授权码

### 3. 运行项目

**方式1: IDEA直接运行**
1. 打开 `PaperPilotApplication.java`
2. 点击类名旁边的绿色箭头 → Run

**方式2: Maven运行**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**验证启动成功**:
```
Server started on port: 8080
```

### 4. 测试API

**推荐工具**: [Apifox](https://www.apifox.cn/) 或 Postman

**基础测试流程**:

1. **发送验证码**:
```bash
POST http://localhost:8080/api/v1/auth/send-code
Content-Type: application/json

{
  "email": "test@example.com"
}
```

2. **注册**:
```bash
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "123456",
  "code": "123456",
  "nickname": "测试用户"
}
```

3. **登录**:
```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "123456"
}
```

4. **查询额度**:
```bash
GET http://localhost:8080/api/v1/user/quota
Authorization: Bearer {accessToken}
```

### 5. 常见问题

**Q: 邮件发送失败？**
- 检查邮箱授权码是否正确
- 确认SMTP服务已开启
- 查看防火墙是否阻挡端口

**Q: Redis连接失败？**
- 确认 `docker-compose ps` 中redis状态为healthy
- 检查端口6379是否被占用

**Q: 数据库连接失败？**
- 确认MySQL容器已启动
- 检查数据库 `paperpilot` 已创建
- 确认用户名密码正确
