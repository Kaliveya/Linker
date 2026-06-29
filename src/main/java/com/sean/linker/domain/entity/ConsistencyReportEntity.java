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
@TableName("consistency_report")
public class ConsistencyReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 被检查的下游节点 */
    private Long nodeId;

    /** 是否对齐 */
    private Boolean aligned;

    /** 问题数（冗余字段，便于前端列表展示） */
    @Builder.Default
    private Integer issueCount = 0;

    /** 最高严重级 HIGH / MEDIUM / LOW，便于按级筛选 */
    private String highestSeverity;

    /** 问题列表 JSON */
    private String issuesJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
