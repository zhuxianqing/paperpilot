# Skill: 在 Claude Code 中添加 MySQL MCP 服务

## 概述

在 Claude Code (CLI) 中通过 MCP (Model Context Protocol) 连接 MySQL 数据库，使 Claude 能够直接执行 SQL 查询和浏览数据库结构。

## 方案选型

| 方案 | npm 包 | 特点 |
|------|--------|------|
| **@bytebase/dbhub (推荐)** | `@bytebase/dbhub` | 稳定可靠，支持多种数据库（MySQL/PostgreSQL/SQLite），通过 DSN 连接串配置 |
| @benborla29/mcp-server-mysql | `@benborla29/mcp-server-mysql` | 通过环境变量配置，仅支持 MySQL，启动兼容性问题较多 |

> 实测推荐使用 `@bytebase/dbhub`，稳定性更好。

## 前置条件

- Node.js >= 18（执行 `node -v` 检查）
- npx 可用（执行 `which npx` 检查）
- 目标 MySQL 服务器网络可达（执行 `nc -z -w 5 <host> <port>` 检查）

## 添加步骤

### 1. 一行命令添加

```bash
claude mcp add-json <服务名称> '{"type":"stdio","command":"npx","args":["-y","@bytebase/dbhub","--dsn","mysql://<用户名>:<密码>@<主机>:<端口>/<数据库名>"]}'
```

### 2. DSN 连接串格式

```
mysql://用户名:密码@主机:端口/数据库名
```

**特殊字符转义规则（URL 编码）：**

| 原字符 | 编码后 | 示例 |
|--------|--------|------|
| `@` | `%40` | `user@db` → `user%40db` |
| `#` | `%23` | `pass#123` → `pass%23123` |
| `:` | `%3A` | `pass:word` → `pass%3Aword` |
| `/` | `%2F` | `a/b` → `a%2Fb` |
| `?` | `%3F` | `pass?1` → `pass%3F1` |
| `%` | `%25` | `100%` → `100%25` |

### 3. 实际示例

```bash
# 示例：添加 poit-factory 数据库
claude mcp add-json mysql '{"type":"stdio","command":"npx","args":["-y","@bytebase/dbhub","--dsn","mysql://poit-factory-dev%40poit_db:Poit%40123456@dev-mysql.poi-t.cn:3306/poit-factory"]}'

# 示例：添加 poit-product 数据库（不同服务名称）
claude mcp add-json mysql-product '{"type":"stdio","command":"npx","args":["-y","@bytebase/dbhub","--dsn","mysql://poit-product%40poit_db:Poit%40123456@dev-mysql.poi-t.cn:3306/poit-product"]}'
```

> **命名规则：** 每个 MCP 服务的名称必须唯一。建议用 `mysql-<业务名>` 格式命名，如 `mysql-product`、`mysql-cloud`、`mysql-factory`。

### 4. 作用域选项

在 `add-json` 后加 `-s <scope>` 指定作用域：

| 参数 | 说明 | 配置文件位置 |
|------|------|-------------|
| `-s local`（默认） | 仅对当前项目生效，私有不提交 Git | `~/.claude.json` (project 段) |
| `-s project` | 项目级，可提交 Git 团队共享 | `.mcp.json` |
| `-s user` | 全局，所有项目生效 | `~/.claude.json` (global 段) |

```bash
# 全局可用
claude mcp add-json mysql -s user '{"type":"stdio","command":"npx","args":["-y","@bytebase/dbhub","--dsn","mysql://..."]}'
```

## 管理命令

```bash
# 查看所有 MCP 服务及连接状态
claude mcp list

# 查看某个服务的详细配置
claude mcp get <服务名称>

# 删除某个 MCP 服务
claude mcp remove <服务名称> -s local

# 在 Claude Code 会话内查看 MCP 状态
/mcp
```

## 提供的工具能力

连接成功后，Claude Code 会话中将自动获得以下工具：

| 工具 | 说明 |
|------|------|
| `execute_sql` | 执行 SQL 查询语句（SELECT / INSERT / UPDATE / DELETE 等） |
| `search_objects` | 搜索数据库对象（schema、table、column、procedure、function、index） |

## 常见问题排查

### Q1: `claude mcp list` 显示 "Failed to connect"

**可能原因：**
- 首次运行 npx 需要下载 npm 包，健康检查超时
- 网络不通

**解决方法：**
```bash
# 1. 先手动预下载包
npx -y @bytebase/dbhub --help

# 2. 检查网络连通性
nc -z -w 5 <host> <port>

# 3. 重启 Claude Code 会话再检查
claude mcp list
```

### Q2: 用户名或密码含有 `@` 等特殊字符

在 DSN 连接串中必须做 URL 编码，`@` → `%40`，否则会解析错误。

### Q3: 同时连接多个数据库

用不同的服务名称分别添加即可，如 `mysql`、`mysql-product`、`mysql-cloud`，它们会同时启动互不影响。

### Q4: 想换用 @benborla29/mcp-server-mysql 方案

```bash
claude mcp add-json mysql '{"type":"stdio","command":"npx","args":["-y","@benborla29/mcp-server-mysql"],"env":{"MYSQL_HOST":"主机","MYSQL_PORT":"端口","MYSQL_USER":"用户名","MYSQL_PASSWORD":"密码","MYSQL_DATABASE":"数据库名"}}'
```

> 注意：此方案通过环境变量传参，特殊字符无需 URL 编码，但实测稳定性不如 dbhub。
