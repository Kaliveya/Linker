# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 项目源代码、注释、提示词全部使用中文。回答用户问题、写注释、与用户沟通时保持中文。代码标识符（类名、字段名、方法名）保持英文。

The repo root `../../README.md` and `../../Linker-3_技术方案.md` are the canonical references for product scope and table-level technical design. This file focuses on what's non-obvious from reading the code or README.

## Commands

```bash
# Local run (requires application-local.yml with secrets — see README §2 "本地配置覆盖")
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Build
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class / method
./mvnw test -Dtest=LinkerApplicationTests
./mvnw test -Dtest=LinkerApplicationTests#contextLoads
```

Server listens on `:8667`. Currently the only test is `LinkerApplicationTests#contextLoads` (Spring context smoke test) — adding real tests requires the full PG + Neo4j + RabbitMQ + Redis stack because of `@SpringBootTest`.

### Required local services

PG (with `vector` extension on db `Linker`), Neo4j 5 (`bolt://localhost:7687`), RabbitMQ (`guest/guest`), Redis. The `application.yml` checked in has `{YOUR_*}` placeholders; `application-local.yml` (gitignored via `*-local.yml`) is required and must override `spring.datasource.password`, `spring.neo4j.authentication.password`, and both `spring.ai.minimax.api-key` + `spring.ai.minimax.embedding.api-key`.

The full DDL lives at `src/main/resources/db/schema.sql` — a single idempotent script that creates all 12 tables (linker_user, project, project_member, project_module, artifact_doc[_version], semantic_node, node_relation, notification, consistency_report, change_event, graph_sync_outbox) plus the pgvector HNSW + anchor + relation lookup indexes. **Not auto-applied** — run by hand: `psql -d Linker -f src/main/resources/db/schema.sql`. Detailed schema design rationale is in `Linker-3_技术方案.md §4.2`.

### Java version note

`pom.xml` declares `java.version=17`; trust the pom — Spring Boot 3.5 only requires 17.

## Architecture: what requires multi-file knowledge

### Two stores, one fact source

PostgreSQL+pgvector is the source of truth (full text, vectors, versions, change events, notifications, candidate edges). Neo4j is a **lightweight projection** of confirmed nodes + confirmed edges, used only for graph traversal (impact propagation in Scenario 3, planned).

- Join key: `semantic_node.id` ↔ `Node.nodeId` (Neo4j `@Id`).
- Sync is **synchronous direct call** in the MVP (`GraphSyncServiceImpl`). Production plan is the `graph_sync_outbox` table (entity already exists) + async retry — not wired yet.
- Neo4j sync failures only log; PG remains authoritative. Do **not** add retry or transaction rollback around `GraphSyncService` calls without coordinating with the outbox plan.
- Edges have **three relation types**, each hardcoded in Cypher (Neo4j requires literal type names): `REFINE` (REQUIREMENT→BLUEPRINT_SEG), `DERIVE` (BLUEPRINT_SEG→TECH_SEG), `IMPLEMENT` (REQUIREMENT→TEST_CASE). See `SemanticGraphRelationRepository`. Adding a new relation type = new method pair (upsert + delete).

### When does a node enter Neo4j?

Only after **fully confirmed**. See `NodeConfirmServiceImpl.syncToNeo4jIfFullyConfirmed`:

- `REQUIREMENT`: `req_type_confirmed=true` **and** `module_status=CONFIRMED`
- `BLUEPRINT_SEG` / `TECH_SEG` / `TEST_CASE`: `module_status=CONFIRMED` only (no reqType on these)

This is why `confirmReqType` and `confirmModule` both call the same sync helper — either confirmation can be the last one. Keep this gate intact when adding new node types.

### RabbitMQ doc pipeline

`DocsController.importDocs` writes `artifact_doc` + `artifact_doc_version` synchronously and publishes a `doc.upload` event. `DocPipelineConsumer.handleDocUpload` switches on `doc.docType` (`RESEARCH` / `BLUEPRINT` / `TECH` / `TEST`) and runs one of four `analyzeXxxDoc` methods. The four follow an identical shape: load module vocabulary → load existing nodes (bizCode dedup) → call ParseAgent → insert SemanticNodeEntity → write embedding via `updateEmbedding` → for downstream types, kick off `RelationCandidateService.suggestRelations`.

The consumer **always acks**, even on exception, to avoid queue blocking (failures are only logged). Re-running failed docs is currently manual.

### ChatClient beans are named — always inject by `@Qualifier`

`ParseAgentConfig` defines four `ChatClient` beans: `requirementParseAgent`, `blueprintParseAgent`, `techParseAgent`, `testParseAgent`. `RelationAgentConfig` defines `relationVerifyAgent`. `ConsistencyAgentConfig` defines `consistencyAgent`. They differ only in `defaultSystem` prompt + structured output type. When injecting, you **must** `@Qualifier("xxxAgent")` — there are multiple `ChatClient` beans so autowire-by-type will fail.

Each bean wires `StructuredOutputValidationAdvisor` with `maxRepeatAttempts=3` for retry-on-bad-JSON. When changing the output DTO, update both the `outputType` `ParameterizedTypeReference` here **and** the `.entity(...)` call at the call site (they're not type-linked at compile time).

### `ModuleVocabularyAdvisor` is per-call, not default

The module enum is project-scoped, so the advisor cannot be a default — it's constructed fresh in `DocPipelineConsumer.analyzeXxxDoc` from `projectModuleMapper` results and attached via `.advisors(new ModuleVocabularyAdvisor(moduleCodes))` on the prompt. It edits the `SystemMessage` in `before(...)` to append the closed-world enum constraint.

If the LLM returns a `module` value not in the enum, the consumer downgrades it to `null` + `moduleStatus=PENDING` (waiting for human pick in `NodeConfirmService.confirmModule`). Do **not** trust LLM output unconditionally.

### `bizCode` is the version-diff stable key — protect it

`R1`/`P1`/`S1` for requirements, `B*` for blueprint, `T*` for tech, `TC*` for test cases. Identifies "same node across versions" for the planned scenario-3 diff. The consumer pre-loads existing bizCodes in the project and injects them into the prompt as "已有节点（bizCode 已占用，新节点不得重复）" so the LLM reuses them for unchanged content. Don't strip this section when refactoring the pipeline.

### Three-layer relation defense (Scenario 2 命门)

`RelationCandidateServiceImpl.suggestRelations`:

1. **Anchor filter (SQL)** — `SemanticNodeMapper.findCandidates` uses pgvector `<=>` cosine distance, but only after `WHERE project_id=? AND module=? AND node_type=?` (+ optional `req_type=?`). The module filter is what prevents "Login OTP" ↔ "Payment OTP" cross-contamination.
2. **LLM bidirectional verify** — `relationVerifyAgent` judges *intent match*, not text similarity. `confidence < 0.6` is dropped.
3. **Human confirm** — Edges go in `node_relation` with `confirmed=false`. `RelationsController` `/api/relations/{relId}/confirm` flips to `true` and (will eventually) trigger `GraphSyncService.upsertEdge` to project into Neo4j.

The upstream type for a downstream is hardcoded in `pickUpstreamType` (BLUEPRINT_SEG→REQUIREMENT, TECH_SEG→BLUEPRINT_SEG, TEST_CASE→REQUIREMENT). `pickUpstreamReqType` further restricts BLUEPRINT_SEG to `BUSINESS` requirements only — PERFORMANCE/SECURITY needs skip the blueprint layer per Scenario 3 design.

### Embedding writes are a separate UPDATE

`SemanticNodeMapper.updateEmbedding` runs `UPDATE semantic_node SET embedding = CAST(? AS vector) WHERE id = ?`. Never write embedding via a normal `updateById` — MyBatis Plus' full-field update will serialize the vector incorrectly (the column is pgvector, not a generic array). `PgVectorTypeHandler` handles read-time parsing of the `[...]` text format.

### What's done vs. what's planned

The README's "MVP 范围与进度" table is the source of truth. Roughly: Scenarios 1 and 2 are end-to-end functional (research ingest → pending confirm → gap assignment; blueprint/tech/test parse → candidate generation → human-confirmed edges sync to Neo4j → consistency check). Scenarios 3 and 5 (version diff, impact propagation, change events) are stubs at best.

There is an entity `ChangeEventEntity` and a `q.pipeline.version` queue binding (`RabbitMQConfig.bindingVersion`), but no consumer for them — that's the Scenario 3 entry point that hasn't been built.

## Conventions and gotchas

- **Layering**: `controller/` → `service/` (interface) + `service/impl/` → `mapper/` (PG, MyBatis Plus) / `repository/` (Neo4j, Spring Data). `domain/dto/` is request shapes, `domain/vo/` is response shapes, `domain/entity/` is PG entities, `domain/graph/` is Neo4j nodes.
- **Response wrapper**: All controllers return `CommonResponse.success(data)` (code `0000`). There is no global error wrapper yet — exceptions bubble as Spring defaults.
- **Lombok everywhere**: `@Data`, `@Builder`, `@RequiredArgsConstructor` for DI. Constructor injection of final fields is the rule; no `@Autowired` on fields.
- **MyBatis Plus**: `LambdaQueryWrapper` / `LambdaUpdateWrapper` are the norm. Raw `@Select` / `@Update` only when the query can't be expressed (e.g. pgvector cosine, vector cast).
- **`map-underscore-to-camel-case: true`** is on globally (`application.yml`). DB columns are snake_case; Java fields are camelCase. Don't add `@TableField("snake_name")` unless the mapping is irregular.
- **`autoResultMap = true`** is required on entities with custom type handlers (see `SemanticNodeEntity`) so MyBatis Plus picks up `@TableField(typeHandler=...)` annotations on result mapping.
- **No global auth / RBAC yet**. `AuthController` + `LinkerUserEntity` exist (login/register) but most endpoints don't enforce identity. Don't assume the caller is authenticated; don't add auth checks ad-hoc — coordinate with the (not-yet-built) RBAC layer.
- **`ParseAgentTools` exists but isn't wired** (`tool/` package) — it's a leftover Spring AI tool-calling scaffold, not used by the four parse agents.
- **Front-end** lives at `../../linker-vue/` (Vue 3) — out of scope here; ignore unless asked.

## Out-of-date parent CLAUDE.md

`../../CLAUDE.md` (repo root) was written before any code existed and still describes the project as "design/planning phase — no code has been written yet". It is incorrect. If a user references it, treat **this** file and the README as authoritative for the Spring Boot module.