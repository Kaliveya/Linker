package com.sean.linker.service;

import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;

public interface GraphSyncService {

    /**
     * 把 PG 节点的轻量投影同步到 Neo4j
     * 在节点的 reqType 和 module 都人工确认后调用
     */
    void upsertNode(SemanticNodeEntity node);

    /**
     * 把 PG 关联边同步到 Neo4j（用户确认建议关联后调用）
     * @return 是否同步成功，调用方据此更新 syncedNeo4j 标志
     */
    boolean upsertEdge(NodeRelationEntity rel);

    /**
     * 从 Neo4j 删除关联边（用户拒绝/删除已确认的关联时调用）
     * 同样返回是否成功
     */
    boolean deleteEdge(NodeRelationEntity rel);
}