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
@TableName("notification")
public class NotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long recipientId;

    /** IMPACT / CONSISTENCY / ASSIGNMENT */
    private String sourceKind;

    private Long relatedNodeId;

    private Long relatedEventId;

    private String title;

    /** 带语义上下文的正文，由 Agent 生成 */
    private String content;

    /** UNREAD / READ / HANDLED / IGNORED */
    @Builder.Default
    private String status = "UNREAD";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
