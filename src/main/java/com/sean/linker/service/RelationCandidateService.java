package com.sean.linker.service;

public interface RelationCandidateService {

    /**
     * 为指定下游节点生成"建议关联"
     * 1. 按 nodeType + reqType 拼装 SQL 锚点（第一重防御）
     * 2. pgvector top-K 检索候选上游
     * 3. 逐条调 RelationVerifyAgent 双向校验（第二重防御）
     * 4. 校验通过的落 node_relation(confirmed=false)，等待人工确认（第三重防御）
     */
    void suggestRelations(Long downstreamNodeId);
}
