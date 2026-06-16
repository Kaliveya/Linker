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
@TableName("change_event")
public class ChangeEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long nodeId;

    /** MODIFY / ADD / DELETE */
    private String changeKind;

    /** DOC_VERSION / QUICK */
    private String sourceType;

    /** 快捷变更时的来源注释（必填） */
    private String sourceNote;

    /** LLM 生成的语义 diff 摘要 */
    private String diffSummary;

    /**
     * PHASE_BA:         BUSINESS 变更，等待 BA 确认业务方案
     * PHASE_DOWNSTREAM: BA 已确认，通知技术/测试层
     * COMPLETED:        全部通知已发出
     * DIRECT:           PERFORMANCE/SECURITY，跳过 BA 层
     */
    @Builder.Default
    private String propagationPhase = "DIRECT";

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}