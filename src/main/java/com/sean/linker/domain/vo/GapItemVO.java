package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 缺口清单项 — 一个需求节点的所有缺口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapItemVO {

    private Long nodeId;
    private String bizCode;
    private String content;
    private String reqType;

    /** 该节点下的所有缺口 */
    private List<MissingItem> gaps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingItem {
        /** 缺失的下游类型 BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
        private String missingType;
        /** 中文显示名 */
        private String missingTypeName;
        /** 建议指派角色 BA / DEV / QA */
        private String suggestedRole;
        /** 候选成员列表，PM 在前端选择具体人 */
        private List<Candidate> candidates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private Long userId;
        private String name;
    }
}