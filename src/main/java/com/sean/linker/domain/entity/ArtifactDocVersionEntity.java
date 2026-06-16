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
@TableName("artifact_doc_version")
public class ArtifactDocVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Integer version;

    /** 原始文件本地路径，demo 阶段不用 MinIO */
    private String filePath;

    private String changeNote;

    private Long authorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}