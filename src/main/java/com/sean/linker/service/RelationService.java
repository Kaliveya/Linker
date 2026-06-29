package com.sean.linker.service;

import com.sean.linker.domain.vo.RelationSuggestionVO;

import java.util.List;

public interface RelationService {

    /** 列出项目下的关联建议，confirmed 传 null 表示不过滤 */
    List<RelationSuggestionVO> listRelations(Long projectId, Boolean confirmed);

    /**
     * 确认建议关联
     * 注：当前阶段只更新 DB，Neo4j 边同步在 Phase 3 完整实现时补
     */
    void confirmRelation(Long relationId);

    /** 拒绝建议关联（物理删除） */
    void rejectRelation(Long relationId);
}
