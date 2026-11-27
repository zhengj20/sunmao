# 榫卯 SunMao

> 房地产行业一体化管理平台 · 把投资测算、成本、招采、计划、费用、销售、资金这些散落的环节，像榫卯一样咬合成一个整体。

榫卯是中国木构的核心工艺：不用一钉一铆，靠构件自身形状互相咬合，就能拼成承重整个屋顶的框架。这个平台想解决的是房地产公司内部的同一件事——**投资、成本、招采、工程、销售、资金各有一套系统和台账，数据对不上，口径不统一**。榫卯把它们放进同一个主数据、同一套审批流、同一个指标体系里。

`AGPL-3.0` · Java 17 / Spring Boot 3.2 · Vue 3 / Vite 5 · MySQL 8 · Flowable 7.1

---

## 它能做什么

| 领域 | 覆盖能力 |
|---|---|
| **主数据 MDM** | 集团/项目/分期/楼栋/房源、供应商与客户库、成本科目树、指标字典、信用与黑名单 |
| **投资测算** | 土地获取到清算的现金流测算、敏感性分析、货值与利润率 |
| **目标成本** | 目标成本版本、合约规划、科目余额、动态成本归集、偏差预警 |
| **成本管理** | 合同台账、变更签证、付款计划与实付、成本月度报告 |
| **招标采购** | 采购需求、资质预审、公开招标/竞谈、回标、评标打分、定标、**围串标识别（同 IP/同报价模式）** |
| **项目计划** | 标准工期库、WBS、里程碑、形象进度、进度调整、预警、工程例会 |
| **费用管理** | 预算与标准、申请、借款、报销、发票池、付款、部门分摊、费用分析 |
| **销售管理** | 房源销控、价格与折扣方案、认购、签约、回款按揭、退房、销售漏斗与报表 |
| **预算管理** | 年度全面预算编制、成本线、执行与控制、超预算刚性拦截 |
| **资金管理** | 资金计划、融资、现金流 |
| **审批工作流** | Flowable 7.1 流程设计器、待办/已办/我发起的、流程监控、业务事件驱动 |
| **AI 数字员工** | 可插拔 LLM 网关（OpenAI 兼容）、审批预审、指标问答、报告生成、知识库、对话、全量调用审计，**带护栏与降级** |
| **数字员工编排** | 技能 / 工具注册、任务与定时调度、多步工作流编排与人工审批节点 |
| **管理驾驶舱** | 集团 / 项目 / 分期三级下钻，指标字典驱动 |
| **CAD 图纸** | 浏览器内 DWG/DXF 预览，免插件 |

### 两条设计主线

**1. 刚性管控规则（而不是软提醒）**
超目标成本、越权限折扣、超预算付款这类动作，在业务规则层直接拦截并强制升级审批，而不是弹个确认框就放过。这是地产成本管控的实际要求。

**2. AI 一律可降级，不阻塞业务**
LLM 网关没有可用配置、超时、非 2xx 或输出置信度过低时，一律返回 `degraded` 标记，调用方回退到规则引擎，业务链路继续走完。护栏在调用前后各做一层：入参敏感数据裁剪（手机号/证件号脱敏）、出参留痕摘要与置信度校验。**API Key 用外部主密钥做 AES/GCM 加密入库，源码里不含任何默认密钥。**

---

## 快速开始

### 环境要求

| | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（推荐 20） |
| MySQL | 8.0+（或直接用 Docker） |

### 1. 起数据库

```bash
docker compose up -d
```

不用 Docker 的话，自建一个库并记住连接信息：

```sql
CREATE DATABASE sunmao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2. 配置密钥

表结构由 Flyway 在首次启动时自动建（77 个迁移脚本），你只需要提供数据库口令和两个密钥。

```bash
cp backend/application-local.yml.example backend/application-local.yml
```

编辑 `backend/application-local.yml`：

```yaml
spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/sunmao?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true"
    username: root
    password: 你的密码

reip:
  jwt:
    secret: "至少 32 字符，用 openssl rand -base64 48 生成"
  ai:
    master-key: "LLM API Key 的入库加密主密钥，留空则 AI 模型配置页无法保存 Key"
```

也可以完全用环境变量：`DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` `JWT_SECRET` `AI_KEY_MASTER_KEY`。

> `application-local.yml` 已在 `.gitignore` 中，不会被提交。

### 3. 起后端与前端

```bash
# 后端 → http://localhost:5221
cd backend && mvn spring-boot:run

# 前端 → http://localhost:5220
cd frontend && npm install && npm run dev
```

Windows 下双击 `start-dev.bat` 可一次拉起两端。

### 4. 登录

打开 <http://localhost:5220>：

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin` | `admin123` | 系统管理员（全权限） |
| `costmgr` | `123456` | 成本经理 |
| `procmgr` | `123456` | 招采经理 |
| `finmgr` | `123456` | 财务经理 |
| `salesmgr` | `123456` | 销售经管 |
| `planmgr` | `123456` | 计划经理 |
| `viewer` | `123456` | 只读 |

首次登录时 `PLAIN:` 前缀的种子口令会被自动升级为 BCrypt 存储。

演示数据自带一个完整的虚构项目「星汇湾」，含合同、招标评标、销售回款、费用报销等闭环单据，登录后直接进「管理驾驶舱」就能看到有内容的图表。

### 5. 接口文档

后端启动后访问 <http://localhost:5221/swagger-ui.html>。

### 启用 AI 能力（可选）

用 `admin` 登录 → 「AI 数字员工 / 模型配置」，填一个 OpenAI 兼容服务（DeepSeek、通义、Kimi、Ollama 等）的 Base URL、API Key 与模型名并启用。**不配置也能用**，所有 AI 页面会自动降级为规则引擎结果并标注降级原因。

---

## 项目结构

```
.
├── backend/                    Spring Boot 3.2 单体
│   ├── pom.xml
│   ├── application-local.yml.example
│   └── src/main/
│       ├── java/com/reip/
│       │   ├── framework/      JWT 鉴权、全局异常、通用响应、拦截器
│       │   ├── platform/       用户/角色/菜单/字典/通知/数据质量
│       │   ├── mdm/            主数据（项目/房源/供应商/客户/科目/指标）
│       │   ├── invest/         投资测算
│       │   ├── cost/ budget/   目标成本 · 动态成本 · 合同 · 变更 · 预算
│       │   ├── proc/           招采（含围串标识别）
│       │   ├── plan/           项目计划 · 里程碑 · 形象进度
│       │   ├── expense/ fund/  费用报销 · 资金计划
│       │   ├── sales/          销控 · 认购签约 · 回款 · 折扣
│       │   ├── workflow/       Flowable 流程与业务事件处理
│       │   ├── ai/             LLM 网关 · 护栏 · 审计 · 预审/问答/报告/知识库
│       │   ├── dwg/ digital/   图纸解析 · 数字员工编排（技能/工具/任务/流程）
│       │   └── portal/         工作台 · 待办 · 驾驶舱
│       └── resources/db/migration/   Flyway V1–V77
├── frontend/                   Vue 3 + Vite + Element Plus，130 个页面
│   └── src/views/<domain>/
├── docker-compose.yml          MySQL 8
└── .github/workflows/ci.yml
```

规模参考：Java 423 文件 / 3.7 万行，Vue 单文件组件 142 个（其中业务页面 130 个）/ 3.9 万行，SQL 迁移 77 个 / 4.9 千行。

> 包名 `com.reip` 与配置前缀 `reip.*` 是项目早期代号 REIP（Real Estate Integrated Platform）的遗留命名，未随品牌名一起改动。

---

## 技术选型

| 层 | 选择 | 说明 |
|---|---|---|
| 后端 | Spring Boot 3.2.5 | |
| 持久层 | MyBatis-Plus 3.5.7 | 逻辑删除、下划线转驼峰 |
| 数据库迁移 | Flyway 9.22 | 77 个脚本，启动即建库 |
| 工作流 | Flowable 7.1.0 社区版 | BPMN 流程设计器 + 事件驱动业务联动 |
| 鉴权 | 自研 JWT（jjwt 0.12.5）+ 角色菜单权限 | 未引入 Spring Security 全家桶 |
| 报表 | Apache POI | 合同/成本/销售导出 |
| 前端 | Vue 3 + Vite 5 + Pinia + Element Plus | |
| 图表 | ECharts 5 + D3 | 驾驶舱、敏感性分析、形象进度 |
| 图纸 | mlightcad 套件 + three.js | 浏览器内渲染 DWG/DXF |

---

## 已知限制

诚实清单，接手前请看：

- **没有自动化测试**。后端 `src/test` 为空，前端无单测。CI 只做编译与构建。
- **单体架构**，按业务域分包而非分服务，没有多租户隔离的强制边界。
- **权限模型只到"角色 → 菜单/按钮"**，没有行级数据权限。
- 演示数据全部虚构，但字段覆盖度是按演示脚本设计的，**不是生产可用的初始化数据**。
- `mysql-connector-j` 为 GPL-2.0 + Universal FOSS Exception，见 [NOTICE](NOTICE)。介意协议的话按 NOTICE 里的说明换 MariaDB 驱动。
- Flowable 表由引擎自建（`database-schema-update: true`），生产环境建议改为由 Flyway 统一管理。

---

## 贡献

分支 → PR，CI 绿了即可评审。提交信息沿用 Conventional Commits（`feat: / fix: / refactor:` + 中文描述）。

**贡献者条款**：向本仓库提交 PR 即视为你授予项目在 AGPL-3.0 之下、以及未来在任何许可证之下使用你贡献代码的永久、免版税、可再许可的权利，并同意项目维护者在不另行征求同意的前提下重新许可这些代码。这是为了保留项目未来换协议或闭源商用的可能性。

---

## 许可证

Copyright © 2026 SunMao Contributors

本程序是自由软件：你可以在自由软件基金会发布的 **GNU AFFERO 通用公共许可证**（第 3 版或更新版本）的条款下重新分发和/或修改它。

选 AGPL 而不是 GPL 的原因：AGPL 补上了网络服务的口子——**任何人把本平台（含修改版）通过网络对外提供服务，都必须同时向使用者提供完整源代码**，包括那些不分发二进制、只跑 SaaS 的用法。完整条款见 [LICENSE](LICENSE)。

第三方组件的许可证清单见 [NOTICE](NOTICE)。
