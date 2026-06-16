package com.sean.linker.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sean.linker.infrastructure.typehandler.PgVectorTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "semantic_node", autoResultMap = true)
public class SemanticNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 来源文档，快捷变更时可为 null */
    private Long docId;

    /** REQUIREMENT / BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
    private String nodeType;

    /** BUSINESS / PERFORMANCE / SECURITY，仅 REQUIREMENT 有意义 */
    private String reqType;

    /** 功能模块，值来自 project_module.code */
    private String module;

    /** CONFIRMED / PENDING（LLM 无法分类时） */
    @Builder.Default
    private String moduleStatus = "CONFIRMED";

    /** false = LLM 初判待确认，true = 人工已确认 */
    @Builder.Default
    private Boolean reqTypeConfirmed = false;

    /** 业务编号，版本比对的对齐键，如 R3 / P1 */
    private String bizCode;

    private String content;

    /** pgvector(1536)，由 EmbeddingModel 生成后写入 */
    @TableField(typeHandler = PgVectorTypeHandler.class)
    private float[] embedding;

    private Long ownerId;

    /** DRAFT / CONFIRMED / DONE */
    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private Integer version = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}