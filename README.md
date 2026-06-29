# Linker — 软件交付的 AI 语义连接器

> 把孤立的需求、方案、测试、代码连成一张能自动校验、自动传播的语义网络

---

## 这是什么

AI 工具让每个角色都能更快产出制品（PM 写需求、BA 写方案、DEV 写代码、QA 写测试），但产出之间是断的：

- 需求改了一句话，没人知道下游哪些方案和测试要跟着改
- 业务方案漏了几条需求，要靠人肉对照才发现
- 项目进度全靠每周例会和自报，没人知道"真实"完成度

Linker 把每份制品的语义单元（一条需求、一段方案、一个测试用例）建模成**图节点**，让 AI 在节点之间持续做三件事：

| 能力 | 解决的问题 |
|---|---|
| **一致性校验** | 业务方案是否完整覆盖需求？技术方案有没有遗漏或凭空新增？ |
| **变更影响传播** | 改一处需求，自动定位所有受影响的下游节点 + 精准通知对应负责人 |
| **真实进度推断** | 从代码提交、测试通过率、文档状态反推进度，而非自报 |

这是 Jira / Confluence 类工具因为不理解语义而做不到的。

---

## 核心数据模型

所有功能都是图操作：

```
需求调研文档
    │ ParseAgent 解析
    ▼
REQUIREMENT 节点  ──细化──▶  BLUEPRINT_SEG  (业务方案段落)
   │                          │
   │                          └─派生─▶  TECH_SEG  (技术方案段落)
   └────────实现────────────────────┴─▶  TEST_CASE
                                       │
                                       └─▶  代码 / PR
```

- **节点** 带向量 Embedding + 结构化元数据（`bizCode`、`reqType`、`module`、`owner`、`status`）
- **边** 由 AI 提议 + 人工确认入库，只有 `confirmed=true` 的边参与变更传播
- **三类关系**：REFINE（需求→业务方案）、DERIVE（业务方案→技术方案）、IMPLEMENT（需求→测试用例）

---

## 三个最难的技术点

### 1. 关联准确性（场景二的命门）

纯向量相似度会把"登录 OTP"和"支付 OTP"误关联——语义极度相似但绝不能串台。三重防御：

```
锚点过滤 (SQL: WHERE module=? AND node_type=? [AND req_type=?])
      ↓
LLM 双向校验 (RelationVerifyAgent 判断意图是否真匹配；confidence < 0.6 丢弃)
      ↓
人工确认 (前端"关联确认"虚线 → 实线 → 同步 Neo4j)
```

`ModuleVocabularyAdvisor` 把模块表的 `code + name` 一并注入 system prompt，并提示"按业务语义归类，不要被关键词字面匹配误导"——避免 LLM 把"找回密码"归到 LOGIN 模块。

`DocPipelineConsumer` 在落库前做 **bizCode 防御性去重**（项目内唯一 + 本批内唯一），兜底 LLM 同批复用编号的边界情况。

### 2. reqType 路由传播（场景三的核心，待实现）

变更传播不是无脑全员广播。按 `reqType` 走不同路径：

- **BUSINESS** 需求变更 → 两阶段门控：先通知 BA 确认业务方案 → 再通知 DEV / QA
- **PERFORMANCE / SECURITY** 需求变更 → 跳过业务方案层，直达技术 / 测试

实现方式：Neo4j 图遍历时按 `reqType` 拼不同的 Cypher 约束（边类型在 Neo4j 必须硬编码，参见 `SemanticGraphRelationRepository`）。

### 3. bizCode 对齐（场景三/五的基石）

文档升版时，识别"哪条改了、哪条是新增、哪条被删了"，靠的是稳定的 `bizCode`（R3、P1、TC_OTP_001）。ParseAgent 解析时强制输出唯一编号，升版时通过 prompt 注入已有 bizCode 列表防止冲突。

---

## 系统架构

```
┌────────────────────────────────────────────────────────┐
│  接口层      Controller / 前端 Vue3 + Element Plus      │
├────────────────────────────────────────────────────────┤
│  应用服务层  上传流水线编排 / 变更传播编排 / 完整性检查    │
├────────────────────────────────────────────────────────┤
│  Agent 层    ParseAgent (×4) / RelationVerifyAgent /    │
│              ConsistencyAgent / ImpactAgent（预留）      │
├────────────────────────────────────────────────────────┤
│  领域服务层  节点管理 / 关联图 / 检索召回 / 变更检测      │
├────────────────────────────────────────────────────────┤
│  基础设施层  PostgreSQL+pgvector │ Neo4j │ MiniMax │ MQ │
└────────────────────────────────────────────────────────┘
```

**双库职责分离**：

| 数据库 | 角色 | 用途 |
|---|---|---|
| PostgreSQL + pgvector | 唯一事实源 | 全文、向量、版本、变更、通知；建图阶段的向量检索 |
| Neo4j | 图投影 | 轻量节点 + 已确认边；变更传播图遍历 |

两库通过 `semantic_node.id ↔ Node.nodeId` 关联。当前 MVP 走同步直调；生产改造走 `graph_sync_outbox` 异步最终一致（表已建好，消费者待实现）。

---

## 技术栈

| 层次 | 选型 |
|---|---|
| 应用框架 | Spring Boot 3.5 |
| AI 编排 | Spring AI 1.1.4（ChatClient / Advisor / BeanOutputConverter） |
| 大模型 | MiniMax-M2.7（OpenAI 兼容） |
| Embedding | MiniMax embo-01（1536 维） |
| 主数据 + 向量 | PostgreSQL 15 + pgvector（HNSW 余弦索引） |
| 图数据库 | Neo4j 5（Spring Data Neo4j） |
| 持久化 | MyBatis Plus 3.5 |
| 消息队列 | RabbitMQ（异步解析流水线） |
| 缓存 | Redis |
| 前端 | Vue 3 + Element Plus + vis-network（深色主题） |

---

## MVP 范围与进度

聚焦四个场景串成的一条完整闭环：

```
场景一 (解析 + reqType识别 + 完整性检查)
   │  产出：带 reqType 的需求节点
   ▼
场景二 (建立关联 + 一致性校验)
   │  产出：已确认的关联边（语义图成形）
   ▼
场景三 (版本比对 → 变更检测 → reqType路由传播 → 通知)
   │  共享版本比对能力
   ▼
场景五 (版本比对 → 新增识别 → 提醒补方案 → 回到完整性检查)
```

### 场景一 · 需求调研阶段（**已完成**）

文档上传 → AI 解析 → 人工确认 → 缺口指派完整闭环：

- [x] 文档上传 + RabbitMQ 异步流水线（`POST /api/projects/{pid}/docs`）
- [x] **ParseAgent**（4 个 Bean，对应 4 类文档）：拆节点 / 识别 reqType / 提取 bizCode
- [x] **ModuleVocabularyAdvisor**：注入 `code + name` 闭域词表，按业务语义归类（防跨模块串台第一重锚点）
- [x] **StructuredOutputValidationAdvisor**：JSON 结构校验 + 失败自动重试
- [x] **bizCode 防御性去重**：落库前过滤同批复用 / 已有 bizCode
- [x] 节点入库 + Embedding 向量化（pgvector）
- [x] 待确认面板 / reqType + module 人工确认（`PATCH /api/nodes/{id}/req-type`、`/module`）
- [x] 两项都确认后同步节点投影到 Neo4j（Spring Data Neo4j + Cypher MERGE）
- [x] 实时缺口计算（`GET /api/projects/{pid}/gaps`，按 reqType 给不同期望下游）
- [x] PM 一键指派 → 生成 ASSIGNMENT 通知（`POST /api/projects/{pid}/assignments`）
- [x] 项目 / 成员 / 模块 / 用户 / 通知 CRUD 接口

### 场景二 · 方案编写阶段（**已完成**）

- [x] 业务方案 / 技术方案 / 测试用例文档上传解析（复用 ParseAgent 结构 + 各自专用 prompt）
- [x] **关联建立**：锚点过滤 + RelationVerifyAgent 双向校验 + 候选落库
- [x] **关联人工确认**：前端"关联确认"页接受/拒绝，确认后同步边到 Neo4j
- [x] **ConsistencyAgent**：上下游一致性校验，输出 NOT_COVERED / CONFLICT / EXTRA
- [x] **应急工具**：图谱节点抽屉支持「修正模块」「重新生成候选关联」

### 场景三 · 业务需求变更（待实现）

- [ ] 文档升版接口（场景三入口一）
- [ ] 快捷变更接口（场景三入口二）
- [ ] 版本比对：按 bizCode 对齐识别 MODIFY / ADD / DELETE
- [ ] **ImpactAgent**：reqType 路由 + Neo4j 图遍历
- [ ] BUSINESS 两阶段门控通知（PHASE_BA → PHASE_DOWNSTREAM）

> `change_event` 表 + `q.pipeline.version` 队列绑定已建好；消费者待补。

### 场景五 · 中途新增需求（待实现）

- [ ] 升版新增节点双重触发：变更事件 + 进缺口清单

---

## 端到端演示路径

走完整的"场景一 + 场景二"闭环，前端操作如下；详细脚本见 `/Users/sean/projects/Linker/test/测试脚本.md`。

```
1. 注册账号 / 登录
2. 项目概览 → 创建项目（创建者自动成为 PM 成员）
3. 项目概览 → 添加功能模块（必须先于上传文档！）
4. 文档导入 → 上传需求调研 .md
       ⏱  等待解析（30-60 秒）
5. 待确认节点 → 逐条选 reqType + module，点「确认」按钮（草稿态，不会即选即生效）
6. 文档导入 → 上传业务方案 .md
       ⏱  等待解析 + 候选关联生成
7. 关联确认 → 接受 AI 给出的"细化"候选关联
8. 文档导入 → 上传技术方案 .md → 关联确认（派生）
9. 文档导入 → 上传测试用例 .md → 关联确认（实现）
10. 语义关联图 → 看图谱；点节点看上下游邻居 + 应急修正
11. 一致性检测 → 跑校验，看 NOT_COVERED / CONFLICT / EXTRA
12. 缺口与指派 → PM 把缺口指派给 DEV / QA
13. 消息中心 → 被指派人看到通知，可标已读 / 已处理 / 忽略
```

对应的接口调用（如果想直接调 API）：

```
POST   /api/auth/register / login                # 注册 / 登录
POST   /api/projects                              # 创建项目
POST   /api/projects/{pid}/members                # 添加成员
POST   /api/projects/{pid}/modules                # 配置模块
POST   /api/projects/{pid}/docs                   # 上传文档（异步流水线）
GET    /api/projects/{pid}/nodes/pending          # 待确认节点
PATCH  /api/nodes/{id}/req-type                   # 确认 reqType
PATCH  /api/nodes/{id}/module                     # 确认 module（也用于事后修正）
POST   /api/nodes/{id}/suggest-relations          # 应急重算候选
GET    /api/projects/{pid}/relations              # 候选关联列表
POST   /api/relations/{relId}/confirm             # 接受关联
DELETE /api/relations/{relId}                     # 拒绝关联
GET    /api/projects/{pid}/graph                  # 语义图谱（节点+边）
GET    /api/nodes/{id}                            # 节点详情（含邻居）
POST   /api/projects/{pid}/consistency/run        # 跑一致性校验
GET    /api/projects/{pid}/consistency/reports    # 校验报告
GET    /api/projects/{pid}/gaps                   # 缺口
POST   /api/projects/{pid}/assignments            # 一键指派
GET    /api/notifications?userId=...              # 我的通知
PATCH  /api/notifications/{id}/status             # 标已读 / 处理 / 忽略
```

---

## 本地启动

### 前置依赖

| 服务 | 版本 | 说明 |
|---|---|---|
| Java | 17+ | Spring Boot 3.5 最低要求；pom 声明的是 17 |
| Node.js | 20+ | 前端 Vite 5 要求 |
| PostgreSQL | 15+ | 需启用 `vector` 扩展 |
| Neo4j | 5+ | `bolt://localhost:7687`，默认密码可设 `linker12345` |
| RabbitMQ | 3.12+ | `guest/guest` 默认账号即可 |
| Redis | 7+ | 现阶段只用默认实例，无 key 命名规范要求 |

### 1. PostgreSQL 准备

```bash
# 1) 建库 + 装扩展
psql -U postgres -c 'CREATE DATABASE "Linker";'
psql -U postgres -d Linker -c 'CREATE EXTENSION IF NOT EXISTS vector;'

# 2) 跑完整建表脚本（含所有表 + 索引）
psql -U postgres -d Linker -f src/main/resources/db/schema.sql
```

或在客户端里 `\i src/main/resources/db/schema.sql`。脚本全部用 `CREATE TABLE/INDEX IF NOT EXISTS`，重复执行不会报错。

> **Neo4j 端不需要预建任何东西**：Spring Data Neo4j 在 `MERGE (n:Node {nodeId: $nodeId})` 时按需创建节点与关系。

### 2. 本地配置覆盖

仓库提交的 `application.yml` 中所有密钥均已脱敏。本地启动时新建 `src/main/resources/application-local.yml` 写入真实配置：

```yaml
spring:
  datasource:
    username: your_pg_user
    password: your_pg_password
  neo4j:
    authentication:
      password: your_neo4j_password
  ai:
    minimax:
      api-key: sk-your-minimax-key
      embedding:
        api-key: sk-your-minimax-key
```

`application-local.yml` 已在 `.gitignore` 中（通过 `*-local.yml` 规则），不会被提交。

### 3. 启动后端

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

监听 `:8667`。或在 IntelliJ Run Configuration 的 `Active profiles` 填 `local`。

### 4. 启动前端

```bash
cd ../../linker-vue
npm install
npm run dev
```

默认监听 `:5173`（被占用时自动滚到 `:5174`）。dev server 已配 `/api` 代理到后端 `:8667`，前端代码直接调 `/api/...` 即可。

---

## 项目结构

### 后端 `linker-springboot/linker/`

```
src/main/java/com/sean/linker/
├── agent/              # Spring AI Agent 定义
│   ├── advisor/        # ModuleVocabularyAdvisor 等
│   └── client/         # ChatClient 配置（ParseAgent×4 / RelationVerifyAgent / ConsistencyAgent）
├── controller/         # REST 接口
├── service/ + impl/    # 业务服务（场景一+二闭环）
├── mapper/             # MyBatis Plus Mapper（PG）
├── repository/         # Spring Data Neo4j Repository
├── domain/
│   ├── dto/            # 请求 DTO + Agent 输出 DTO
│   ├── vo/             # 返回 VO
│   ├── entity/         # PG Entity
│   └── graph/          # Neo4j 节点投影
├── config/             # RabbitMQConfig / MybatisMetaHandler（字段自动填充）
└── infrastructure/
    ├── mq/             # RabbitMQ 消费者（解析流水线）
    └── typehandler/    # PgVectorTypeHandler

src/main/resources/
├── application.yml     # 通用配置（密钥已脱敏）
├── application-local.yml  # 本地覆盖（gitignore）
└── db/
    └── schema.sql      # 完整建表脚本（含索引）
```

### 前端 `linker-vue/`

```
src/
├── api/                # axios 封装 + 各资源接口
├── stores/             # Pinia store（session / projects）
├── styles/             # 全局深色主题（theme.css）
├── utils/labels.ts     # 枚举 ↔ 中文映射（nodeType / reqType / role 等）
├── components/         # 通用组件（NoProject 空状态）
├── views/              # 8 个业务页面
├── router/             # 路由 + 登录守卫
├── App.vue             # 顶栏 + 侧栏外壳
└── main.ts             # 启动入口
```

---

## 设计文档

| 文档 | 说明 |
|---|---|
| `../../Linker-1_需求文档.md` | 问题定义、定位、核心价值 |
| `../../Linker-2_业务方案.md` | 系统架构、11 个功能模块、演示流程 |
| `../../Linker-3_技术方案.md` | MVP 技术实现：表结构、Agent 设计、接口列表 |
| `../../test/测试脚本.md` | 端到端 demo 走法 + 样例文档 |
