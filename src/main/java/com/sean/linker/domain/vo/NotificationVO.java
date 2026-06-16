package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    private Long id;
    private Long projectId;

    /** IMPACT / CONSISTENCY / ASSIGNMENT */
    private String sourceKind;
    private Long relatedNodeId;
    private String title;
    private String content;

    /** UNREAD / READ / HANDLED / IGNORED */
    private String status;
    private LocalDateTime createdAt;
}
