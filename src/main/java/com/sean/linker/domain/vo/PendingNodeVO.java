package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待确认节点 — reqType 未确认 或 module=PENDING
 * 前端按 nodeType 区分渲染：REQUIREMENT 需要选 reqType + module，其余只需选 module
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingNodeVO {

    private Long nodeId;
    private String bizCode;
    private String content;

    /** REQUIREMENT / BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
    private String nodeType;

    /** LLM 初判的 reqType — 只有 REQUIREMENT 才有 */
    private String reqType;
    private Boolean reqTypeConfirmed;

    private String module;
    /** CONFIRMED / PENDING */
    private String moduleStatus;
}