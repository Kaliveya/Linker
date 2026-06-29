-- ============================================================
-- Linker 完整建库脚本
-- ============================================================
-- 用法：
--   1. 先建数据库 + 装 pgvector 扩展（见 README §"PostgreSQL 准备"）
--   2. \c Linker
--   3. \i src/main/resources/db/schema.sql
--
-- 脚本采用 IF NOT EXISTS，可重复执行不会报错。
-- 升级表结构时建议先 DROP 再重跑（demo 阶段不做 migration）。
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 0. 扩展
-- ─────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS vector;


-- ─────────────────────────────────────────────────────────────
-- 1. 用户与项目
-- ─────────────────────────────────────────────────────────────

-- 用户账号（Demo 阶段：邮箱密码，密码 SHA-256 入库，无 RBAC）
CREATE TABLE IF NOT EXISTS linker_user (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    email           VARCHAR(128) NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 项目
CREATE TABLE IF NOT EXISTS project (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    current_phase   VARCHAR(32),                       -- RESEARCH / BLUEPRINT / DEVELOPMENT
    created_by      BIGINT,                            -- linker_user.id
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 项目成员（创建项目时创建者自动以 PM 入表）
CREATE TABLE IF NOT EXISTS project_member (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL              -- BA / PM / DEV / QA
);
CREATE INDEX IF NOT EXISTS idx_project_member_project ON project_member (project_id);
CREATE INDEX IF NOT EXISTS idx_project_member_user    ON project_member (user_id);

-- 项目功能模块（闭域词表，ParseAgent 解析前会读取注入到 prompt）
CREATE TABLE IF NOT EXISTS project_module (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    code            VARCHAR(64)  NOT NULL,             -- LOGIN / PAYMENT / ...
    name            VARCHAR(64)  NOT NULL,             -- 登录 / 支付 / ...
    UNIQUE (project_id, code)
);


-- ─────────────────────────────────────────────────────────────
-- 2. 文档与版本（文档导入流水线）
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS artifact_doc (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    doc_type        VARCHAR(16)  NOT NULL,             -- RESEARCH / BLUEPRINT / TECH / TEST
    title           VARCHAR(255),
    current_ver     INTEGER      NOT NULL DEFAULT 1,
    sync_status     VARCHAR(16)  NOT NULL DEFAULT 'SYNCED', -- SYNCED / NEED_UPDATE
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_artifact_doc_project ON artifact_doc (project_id, doc_type);

CREATE TABLE IF NOT EXISTS artifact_doc_version (
    id              BIGSERIAL    PRIMARY KEY,
    doc_id          BIGINT       NOT NULL,
    version         INTEGER      NOT NULL,
    file_path       VARCHAR(512),                      -- demo 阶段本地路径，生产换成对象存储 key
    change_note     TEXT,
    author_id       BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (doc_id, version)
);


-- ─────────────────────────────────────────────────────────────
-- 3. 语义节点 + 关联边（系统核心）
-- ─────────────────────────────────────────────────────────────

-- 语义节点 —— PG 是事实源，Neo4j 是已确认节点的轻量投影
CREATE TABLE IF NOT EXISTS semantic_node (
    id                  BIGSERIAL    PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    doc_id              BIGINT,                                -- 关联到 artifact_doc，快捷变更可为 null
    node_type           VARCHAR(16)  NOT NULL,                 -- REQUIREMENT / BLUEPRINT_SEG / TECH_SEG / TEST_CASE
    req_type            VARCHAR(16),                           -- BUSINESS / PERFORMANCE / SECURITY（仅 REQUIREMENT 有效）
    req_type_confirmed  BOOLEAN      NOT NULL DEFAULT FALSE,   -- 人工是否已确认 reqType
    module              VARCHAR(64),                           -- 取值来自 project_module.code
    module_status       VARCHAR(16)  NOT NULL DEFAULT 'CONFIRMED', -- CONFIRMED / PENDING（LLM 无法分类时）
    biz_code            VARCHAR(32)  NOT NULL,                 -- 版本比对的对齐键：R1 / B2 / T3 / TC1
    content             TEXT,
    embedding           VECTOR(1536),                          -- MiniMax embo-01 输出维度
    owner_id            BIGINT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT / CONFIRMED / DONE
    version             INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- 关联候选锚点过滤：findCandidates 走这条复合索引（命门）
CREATE INDEX IF NOT EXISTS idx_semantic_node_anchor
    ON semantic_node (project_id, module, node_type, req_type);
-- pgvector HNSW 索引（余弦距离）—— 没这个的话候选检索会全表扫
CREATE INDEX IF NOT EXISTS idx_semantic_node_embedding_hnsw
    ON semantic_node USING hnsw (embedding vector_cosine_ops);
-- 待确认列表 / bizCode 查询
CREATE INDEX IF NOT EXISTS idx_semantic_node_project_type
    ON semantic_node (project_id, node_type, biz_code);

-- 关联边 —— AI 候选 + 人工确认 + Neo4j 同步状态
CREATE TABLE IF NOT EXISTS node_relation (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    from_node_id    BIGINT       NOT NULL,                    -- 上游节点
    to_node_id      BIGINT       NOT NULL,                    -- 下游节点
    relation_type   VARCHAR(16)  NOT NULL,                    -- REFINE / DERIVE / IMPLEMENT
    confidence      REAL,                                     -- LLM 双向校验置信度
    confirmed       BOOLEAN      NOT NULL DEFAULT FALSE,      -- 人工确认才同步到 Neo4j 并参与传播
    reason          TEXT,                                     -- LLM 判定理由
    synced_neo4j    BOOLEAN      NOT NULL DEFAULT FALSE,      -- 是否已成功投影到 Neo4j
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_node_relation_to_node
    ON node_relation (to_node_id, confirmed);
CREATE INDEX IF NOT EXISTS idx_node_relation_from_node
    ON node_relation (from_node_id, confirmed);
CREATE INDEX IF NOT EXISTS idx_node_relation_project
    ON node_relation (project_id, confirmed);


-- ─────────────────────────────────────────────────────────────
-- 4. 通知中心
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS notification (
    id                BIGSERIAL    PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    recipient_id      BIGINT       NOT NULL,
    source_kind       VARCHAR(16)  NOT NULL,           -- ASSIGNMENT / IMPACT / CONSISTENCY
    related_node_id   BIGINT,
    related_event_id  BIGINT,                          -- 指向 change_event.id（场景三）
    title             VARCHAR(255),
    content           TEXT,
    status            VARCHAR(16)  NOT NULL DEFAULT 'UNREAD', -- UNREAD / READ / HANDLED / IGNORED
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notification_recipient
    ON notification (recipient_id, status, created_at DESC);


-- ─────────────────────────────────────────────────────────────
-- 5. 一致性报告（场景二 · ConsistencyAgent 产物）
-- ─────────────────────────────────────────────────────────────
-- 每次跑 ConsistencyAgent 都会生成一份报告，旧报告不删除（保留历史）

CREATE TABLE IF NOT EXISTS consistency_report (
    id                BIGSERIAL    PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    node_id           BIGINT       NOT NULL,           -- 被检查的下游节点
    aligned           BOOLEAN      NOT NULL,
    issue_count       INTEGER      NOT NULL DEFAULT 0,
    highest_severity  VARCHAR(16),                     -- HIGH / MEDIUM / LOW / NULL
    issues_json       TEXT,                            -- 问题列表 JSON
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- 按节点 + 时间倒序查最新报告
CREATE INDEX IF NOT EXISTS idx_consistency_report_node
    ON consistency_report (node_id, created_at DESC);
-- 按项目 + 严重级筛选
CREATE INDEX IF NOT EXISTS idx_consistency_report_project_severity
    ON consistency_report (project_id, highest_severity);


-- ─────────────────────────────────────────────────────────────
-- 6. 变更事件 + Neo4j 同步 Outbox（场景三/五 预留，当前消费者尚未实现）
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS change_event (
    id                BIGSERIAL    PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    node_id           BIGINT       NOT NULL,
    change_kind       VARCHAR(16)  NOT NULL,           -- MODIFY / ADD / DELETE
    source_type       VARCHAR(16)  NOT NULL,           -- DOC_VERSION / QUICK
    source_note       TEXT,                            -- 快捷变更必填
    diff_summary      TEXT,                            -- LLM 生成的语义 diff 摘要
    propagation_phase VARCHAR(32)  NOT NULL DEFAULT 'DIRECT',
                                                       -- PHASE_BA / PHASE_DOWNSTREAM / COMPLETED / DIRECT
    created_by        BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_change_event_node
    ON change_event (node_id, created_at DESC);

-- Neo4j 异步同步出口表：当前 MVP 走同步直调，这张表为生产改造预留
CREATE TABLE IF NOT EXISTS graph_sync_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    op_type         VARCHAR(32)  NOT NULL,             -- UPSERT_NODE / DELETE_NODE / UPSERT_EDGE / DELETE_EDGE
    payload         TEXT         NOT NULL,             -- JSON
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / DONE / FAILED
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_graph_sync_outbox_status
    ON graph_sync_outbox (status, created_at);


-- ─────────────────────────────────────────────────────────────
-- 完成
-- ─────────────────────────────────────────────────────────────
-- Neo4j 端不需要预建表/约束：Spring Data Neo4j 在 upsertNode/upsertEdge 的
-- Cypher 中已经用 MERGE (n:Node {nodeId: $nodeId})，节点和关系按需创建。
