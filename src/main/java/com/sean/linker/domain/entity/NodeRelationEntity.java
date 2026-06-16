package com.sean.linker.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("node_relation")
public class NodeRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 上游节点 */
    private Long fromNodeId;

    /** 下游节点 */
    private Long toNodeId;

    /** REFINE / DERIVE / IMPLEMENT */
    private String relationType;

    /** LLM 双向校验置信度 */
    private Float confidence;

    /** false = 建议关联待确认，true = 人工已确认，才同步到 Neo4j 并参与传播 */
    @Builder.Default
    private Boolean confirmed = false;

    /** 双向校验理由 */
    private String reason;

    @Builder.Default
    private Boolean syncedNeo4j = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}