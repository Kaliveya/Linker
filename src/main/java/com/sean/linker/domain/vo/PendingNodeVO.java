package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待确认节点 — reqType 未确认 或 module=PENDING
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingNodeVO {

    private Long nodeId;
    private String bizCode;
    private String content;

    /** LLM 初判的 reqType */
    private String reqType;
    private Boolean reqTypeConfirmed;

    private String module;
    /** CONFIRMED / PENDING */
    private String moduleStatus;
}