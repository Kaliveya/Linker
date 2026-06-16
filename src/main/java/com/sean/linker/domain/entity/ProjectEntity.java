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
@TableName("project")
public class ProjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 当前阶段，如 RESEARCH / BLUEPRINT / DEVELOPMENT */
    private String currentPhase;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}