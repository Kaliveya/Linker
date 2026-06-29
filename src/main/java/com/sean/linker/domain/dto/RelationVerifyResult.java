package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationVerifyResult {

    /**
     * 下游是否真的在落实/实现/覆盖上游意图
     * 不是文本相似度判断，而是意图判断
     */
    private Boolean match;

    /**
     * 置信度 0.0 - 1.0
     * RelationCandidateService 用阈值过滤，低于阈值直接丢弃
     */
    private Float confidence;

    /**
     * 判定理由，一句话中文说明
     * 落 node_relation.reason，供人工确认时参考
     */
    private String reason;
}