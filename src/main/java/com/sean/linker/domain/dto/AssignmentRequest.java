package com.sean.linker.domain.dto;

import lombok.Data;

/**
 * 一键指派的单项请求
 */
@Data
public class AssignmentRequest {

    private Long nodeId;
    private Long assigneeId;
    /** 缺口类型 BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
    private String taskType;
}