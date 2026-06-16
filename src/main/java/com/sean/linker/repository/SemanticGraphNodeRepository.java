package com.sean.linker.repository;

import com.sean.linker.domain.graph.SemanticGraphNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface SemanticGraphNodeRepository extends Neo4jRepository<SemanticGraphNode, Long> {

    /**
     * Upsert：节点存在则更新属性，不存在则新建
     * 用于节点 reqType + module 都确认后的同步
     */
    @Query("""
            MERGE (n:Node {nodeId: $nodeId})
            SET n.projectId = $projectId,
                n.nodeType  = $nodeType,
                n.reqType   = $reqType,
                n.module    = $module,
                n.owner     = $owner,
                n.status    = $status
            RETURN n
            """)
    SemanticGraphNode upsertNode(@Param("nodeId") Long nodeId,
                                 @Param("projectId") Long projectId,
                                 @Param("nodeType") String nodeType,
                                 @Param("reqType") String reqType,
                                 @Param("module") String module,
                                 @Param("owner") Long owner,
                                 @Param("status") String status);

    /**
     * 删除节点及其所有边，节点在 PG 端被删除时调用
     */
    @Query("""
            MATCH (n:Node {nodeId: $nodeId})
            DETACH DELETE n
            """)
    void deleteByNodeId(@Param("nodeId") Long nodeId);
}