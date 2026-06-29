package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 建议关联视图 — 前端关联确认页用
 * 把 node_relation + 两端 semantic_node 信息打平，便于直接展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationSuggestionVO {

    private Long id;

    // 上游节点
    private Long fromNodeId;
    private String fromBizCode;
    private String fromContent;
    private String fromNodeType;

    // 下游节点
    private Long toNodeId;
    private String toBizCode;
    private String toContent;
    private String toNodeType;

    /** REFINE / DERIVE / IMPLEMENT */
    private String relationType;
    private Float confidence;
    private Boolean confirmed;
    private String reason;
    private LocalDateTime createdAt;
}
