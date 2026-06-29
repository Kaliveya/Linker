package com.sean.linker.repository;

import com.sean.linker.domain.graph.SemanticGraphNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

/**
 * Neo4j 关联边操作
 * Neo4j 的边类型必须在 Cypher 里硬编码，所以一个 relationType 一组方法。
 * 当前业务里 relationType 只有三种：REFINE / DERIVE / IMPLEMENT。
 *
 * 所有 upsert 都用 MATCH...MATCH...MERGE 模式：
 * 两端节点必须先存在，缺一端 Cypher 不执行（保护图的完整性）。
 */
public interface SemanticGraphRelationRepository extends Neo4jRepository<SemanticGraphNode, Long> {

    // ───────────── REFINE：上游需求 → 下游业务方案段 ─────────────

    @Query("""
            MATCH (a:Node {nodeId: $fromId})
            MATCH (b:Node {nodeId: $toId})
            MERGE (a)-[r:REFINE]->(b)
            SET r.confidence = $confidence,
                r.confirmed = true
            """)
    void upsertRefine(@Param("fromId") Long fromId,
                      @Param("toId") Long toId,
                      @Param("confidence") Float confidence);

    @Query("""
            MATCH (:Node {nodeId: $fromId})-[r:REFINE]->(:Node {nodeId: $toId})
            DELETE r
            """)
    void deleteRefine(@Param("fromId") Long fromId,
                      @Param("toId") Long toId);

    // ───────────── DERIVE：业务方案段 → 技术方案段 ─────────────

    @Query("""
            MATCH (a:Node {nodeId: $fromId})
            MATCH (b:Node {nodeId: $toId})
            MERGE (a)-[r:DERIVE]->(b)
            SET r.confidence = $confidence,
                r.confirmed = true
            """)
    void upsertDerive(@Param("fromId") Long fromId,
                      @Param("toId") Long toId,
                      @Param("confidence") Float confidence);

    @Query("""
            MATCH (:Node {nodeId: $fromId})-[r:DERIVE]->(:Node {nodeId: $toId})
            DELETE r
            """)
    void deleteDerive(@Param("fromId") Long fromId,
                      @Param("toId") Long toId);

    // ───────────── IMPLEMENT：需求 → 测试用例 ─────────────

    @Query("""
            MATCH (a:Node {nodeId: $fromId})
            MATCH (b:Node {nodeId: $toId})
            MERGE (a)-[r:IMPLEMENT]->(b)
            SET r.confidence = $confidence,
                r.confirmed = true
            """)
    void upsertImplement(@Param("fromId") Long fromId,
                         @Param("toId") Long toId,
                         @Param("confidence") Float confidence);

    @Query("""
            MATCH (:Node {nodeId: $fromId})-[r:IMPLEMENT]->(:Node {nodeId: $toId})
            DELETE r
            """)
    void deleteImplement(@Param("fromId") Long fromId,
                         @Param("toId") Long toId);
}
