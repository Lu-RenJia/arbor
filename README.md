<p align="center">
  <h1 align="center">🌳 Arbor</h1>
  <p align="center"><strong>轻量级数据集成平台</strong> — 可视化编排，流程驱动，开箱即用</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JDK-21-orange?logo=openjdk" alt="JDK 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?logo=springboot" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/Vue-3.x-4fc08d?logo=vuedotjs" alt="Vue 3">
  <img src="https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql" alt="MySQL 8">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License">
</p>

---

## ✨ 特性

- **🔄 可视化流程编排** — 拖拽式节点设计器，构建数据集成流程从未如此简单
- **⚙️ 自研轻量流程引擎** — 顺序执行、事务保障、执行日志全链路追踪
- **🔌 多数据源支持** — MySQL、Oracle、达梦、PostgreSQL，连接池统一管理
- **📦 丰富的节点类型** — 数据库查询、Groovy 脚本、API 调用、Redis 操作、子流程引用
- **📊 Excel 文件处理** — 上传/解析/导出 Excel，支持 Groovy 脚本自定义转换逻辑，执行结果以文件形式下载
- **🔐 安全认证** — JWT + BCrypt 认证，AES 加密存储敏感数据
- **📋 多租户隔离** — 基于用户的数据隔离，支持应用市场共享
- **⏰ 定时调度** — Cron 表达式驱动的定时任务，流程自动执行
- **🎨 现代化前端** — Vue 3 + TypeScript + NaiveUI，响应式交互体验

## 🚀 快速开始

### 环境要求

| 依赖 | 版本              |
|------|-----------------|
| JDK | 21+             |
| Maven | 3.8+            |
| MySQL | 8.0+            |
| Redis | 6.0+            |
| Node.js | 22.20.0+ (前端开发) |

### 1. 克隆项目

```bash
git clone <repo-url>
cd arbor
```

### 2. 初始化数据库

执行arbor-server/sql内的sql

### 3. 启动后端

```bash
export JAVA_HOME=/path/to/jdk21
cd arbor-server
mvn spring-boot:run
```

服务启动后访问 `http://localhost:8088`。

### 4. 启动前端（开发模式）

```bash
cd arbor-ui
npm install
npm run dev
```

### 5. 登录

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | 管理员 |
| `test` | `test` | 普通用户 |

## 🏗️ 架构概览

```
┌──────────────────────────────────────────────────────────┐
│                      Arbor UI                            │
│              Vue 3 + TypeScript + NaiveUI                │
│         流程设计器 · 服务管理 · 执行日志 · 应用市场         │
└──────────────────────┬───────────────────────────────────┘
                       │ REST API (JWT Auth)
┌──────────────────────┴───────────────────────────────────┐
│                    Arbor Server                          │
│                   Spring Boot 4.1                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │                 Flow Engine                      │    │
│  │  START → Node₁ → Node₂ → ... → END              │    │
│  │         (顺序执行 · 事务 · 日志)                  │    │
│  └─────────────────────────────────────────────────┘    │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐    │
│  │ 动态数据源 │ │  Groovy   │ │    定时调度 (Cron)    │    │
│  │  管理     │ │  脚本引擎  │ │                      │    │
│  └──────────┘ └──────────┘ └──────────────────────┘    │
└──────────────┬──────────────────┬────────────────────────┘
               │                  │
         ┌─────┴─────┐     ┌─────┴─────┐
         │  MySQL 8  │     │  Redis    │
         │  业务库   │     │  Token/缓存│
         └───────────┘     └───────────┘
```

## 📦 节点类型

Arbor 内置 **10 种节点**，覆盖常见数据集成场景：

| 节点 | 类型 | 说明 |
|------|------|------|
| 🔵 START | 开始 | 流程入口，可配置初始化参数 |
| 🔴 END | 结束 | 流程出口，汇总输出结果 |
| 🗄️ DB_QUERY | 数据库查询 | 执行 SELECT 查询，支持动态 SQL |
| 📜 GROOVY_SCRIPT | Groovy 脚本 | 内联 Groovy 代码，支持复杂处理逻辑 |
| 📡 API_SEND | API 调用 | 发送 HTTP 请求到外部接口 |
| 📄 REF_DB_SCRIPT | 引用数据库脚本 | 引用预定义的 SQL 脚本 |
| ⚡ REF_PROGRAM_SCRIPT | 引用程序脚本 | 引用预定义的 Groovy 脚本 |
| 🔗 REF_API | 引用 API 配置 | 引用预定义的 API 配置 |
| 📦 REDIS | Redis 操作 | 读写 Redis 缓存数据 |
| 🔁 REF_FLOW | 子流程引用 | 引用并执行另一个流程 |

## 📄 License

MIT License — 详见 [LICENSE](LICENSE) 文件。

---

<p align="center"><em>Made with ❤️ by gwl</em></p>
