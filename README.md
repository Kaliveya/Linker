# Linker — AI 驱动的语义连接器

Linker 是面向软件交付团队的 AI 语义连接平台。核心洞察：AI 工具提升了个人生产力，但各角色产出的制品（会议纪要→需求→技术方案→测试用例→代码）彼此孤立——没有跨制品一致性校验、没有精准变更传播、没有客观进度追踪。

Linker 通过将每份制品的语义单元建模为图节点，让 AI 持续执行传统工具（Jira、Confluence）无法完成的三件事：**一致性校验**、**变更影响传播**、**真实进度推断**。

---

## 核心数据模型

所有功能基于**语义关联图**运作：

```
会议纪要 ──→ 需求节点（蓝图）
                  │
                  ├──→ 技术方案段落 ──→ 代码 / PR
                  │
                  └──→ 测试用例
```

- **Node**：制品的语义单元（一条需求、一段技术设计、一个测试用例），含向量 Embedding + 结构化元数据（module、reqType、bizCode、owner、status）
- **Edge**：节点间的派生/实现关系，人工确认后固化入图，驱动变更传播

---

## 系统架构

```
┌──────────────────────────────────────────┐
│  接口层    Controller / WebSocket         │
├──────────────────────────────────────────┤
│  应用服务层  编排流水线、变更传播、完整性检查  │
├──────────────────────────────────────────┤
│  Agent 层   ParseAgent / RelationVerify  │
│             ImpactAgent / Consistency    │
├──────────────────────────────────────────┤
│  领域服务层  节点管理 / 关联图 / 变更检测    │
├──────────────────────────────────────────┤
│  基础设施层  PostgreSQL+pgvector / Neo4j  │
│             MiniMax LLM / RabbitMQ       │
└──────────────────────────────────────────┘
```

### 双库职责分离

| 数据库 | 职责 |
|---|---|
| PostgreSQL + pgvector | 唯一事实源：节点全文、向量、版本、变更、通知 |
| Neo4j | 图投影：轻量节点 + 关系边，专职变更传播图遍历 |

---

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 应用框架 | Spring Boot 3.5.x |
| AI 编排 | Spring AI 1.1.4 |
| 大模型 | MiniMax（OpenAI 兼容接入） |
| Embedding | MiniMax embo-01（1536 维） |
| 主数据 / 向量 | PostgreSQL + pgvector |
| 图数据库 | Neo4j |
| 持久化 | MyBatis Plus 3.5.x |
| 消息队列 | RabbitMQ |
| 实时推送 | WebSocket（STOMP） |

---

## 当前实现进度（MVP）

### 场景一：需求文档解析（进行中）

- [x] 文档上传接口 `POST /api/projects/{pid}/docs`
- [x] ParseAgent：文档解析为语义节点，识别 reqType / bizCode / module
- [x] ModuleVocabularyAdvisor：module 闭域校验，防跨模块串台
- [x] StructuredOutputValidationAdvisor：JSON 格式校验 + 自动重试
- [x] 节点入库（semantic_node）+ Embedding 向量化（pgvector）
- [x] MQ 异步流水线（RabbitMQ）
- [ ] 完整性检查（缺口算法）
- [ ] 人工确认 reqType / module 接口
- [ ] 缺口清单接口 + 一键指派

### 场景二：关联建立与一致性校验（待实现）

- [ ] 业务方案 / 技术方案上传解析
- [ ] 锚点过滤 + RelationVerifyAgent 双向校验
- [ ] 人工确认关联 → 同步 Neo4j
- [ ] ConsistencyAgent 一致性校验

### 场景三：变更传播（待实现）

- [ ] 文档升版 + 版本比对（bizCode 对齐）
- [ ] 快捷变更入口
- [ ] reqType 路由传播（Neo4j 图遍历）
- [ ] BUSINESS 两阶段门控通知

### 场景五：新增需求识别（待实现）

- [ ] 升版新增节点双重触发（变更通知 + 缺口清单）

---

## 本地启动

### 前置依赖

- Java 21+
- PostgreSQL 15+（需启用 pgvector 扩展）
- Neo4j 5+
- RabbitMQ 3.12+
- Redis 7+

### 数据库初始化

```sql
-- PostgreSQL
CREATE EXTENSION IF NOT EXISTS vector;

-- 建表 DDL 见 /docs/schema.sql（待补充）
```

### 配置

复制 `application.yml` 并填写以下配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/Linker
    username: your_username
    password: your_password
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: your_password
  ai:
    minimax:
      api-key: your_api_key
```

### 启动

```bash
./mvnw spring-boot:run
```

---

## 项目文档

| 文档 | 说明 |
|---|---|
| `Linker-需求文档.md` | 问题、定位、核心价值 |
| `Linker-业务方案.md` | 系统架构、11 个功能模块、演示流程 |
| `Linker-3_技术方案.md` | MVP 技术实现方案 |