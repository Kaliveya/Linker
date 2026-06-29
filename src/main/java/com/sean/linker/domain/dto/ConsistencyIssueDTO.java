package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ConsistencyAgent 的输出格式
 * 一次输入对应一份报告，报告内含若干 issue
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsistencyIssueDTO {

    /** 是否对齐，true 时 issues 应为空 */
    private Boolean aligned;

    /** 问题列表 */
    private List<Issue> issues;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Issue {
        /**
         * 问题类型：
         * NOT_COVERED  下游未覆盖上游某个关键点
         * CONFLICT     下游与上游冲突（参数/约束不一致）
         * EXTRA        下游凭空新增了上游未要求的内容
         */
        private String type;

        /** 在节点正文中的定位说明，如"第 2 段：OTP 有效期" */
        private String location;

        /** 一句话说明问题 */
        private String description;

        /**
         * 严重级别：
         * HIGH    必须修正
         * MEDIUM  应当关注
         * LOW     建议优化
         */
        private String severity;
    }
}
