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
@TableName("artifact_doc")
public class ArtifactDocEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** RESEARCH / BLUEPRINT / TECH / TEST */
    private String docType;

    private String title;

    @Builder.Default
    private Integer currentVer = 1;

    /** SYNCED / NEED_UPDATE */
    @Builder.Default
    private String syncStatus = "SYNCED";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}