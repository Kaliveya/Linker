package com.sean.linker.domain.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j 中的语义节点投影
 * 不存全文与向量，仅保留遍历必需的轻量元数据
 * 对应 Postgres semantic_node 表，通过 nodeId 双向关联
 */
@Node("Node")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticGraphNode {

    /** 与 Postgres semantic_node.id 一致 */
    @Id
    private Long nodeId;

    @Property("projectId")
    private Long projectId;

    /** REQUIREMENT / BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
    @Property("nodeType")
    private String nodeType;

    /** BUSINESS / PERFORMANCE / SECURITY，reqType 路由依据 */
    @Property("reqType")
    private String reqType;

    /** 功能模块 code，关联建立的锚点 */
    @Property("module")
    private String module;

    @Property("owner")
    private Long owner;

    /** DRAFT / CONFIRMED / DONE */
    @Property("status")
    private String status;
}