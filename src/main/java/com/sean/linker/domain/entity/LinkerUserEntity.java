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
@TableName("linker_user")
public class LinkerUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String email;

    private String passwordHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}