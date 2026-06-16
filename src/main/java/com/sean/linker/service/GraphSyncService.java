package com.sean.linker.service;

import com.sean.linker.domain.entity.SemanticNodeEntity;

public interface GraphSyncService {

    /**
     * 把 PG 节点的轻量投影同步到 Neo4j
     * 在节点的 reqType 和 module 都人工确认后调用
     */
    void upsertNode(SemanticNodeEntity node);
}