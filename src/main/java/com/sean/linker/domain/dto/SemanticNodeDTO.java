package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticNodeDTO {

    /**
     * 业务编号，如 R3、P1、S1
     * ParseAgent 输出的核心字段，版本比对的对齐键
     * 同一条需求多次解析必须保持一致
     */
    private String bizCode;

    /**
     * 节点类型
     * ParseAgent 固定输出 REQUIREMENT
     * 其他类型（BLUEPRINT_SEG/TECH_SEG/TEST_CASE）由其他流程产生
     */
    private String nodeType;

    /**
     * 需求类型，决定 reqType 路由路径
     * BUSINESS / PERFORMANCE / SECURITY
     * LLM 初判，前端确认后才生效
     */
    private String reqType;

    /**
     * 所属功能模块，如"登录"/"支付"/"权限"
     * 关联准确性保障的结构化锚点
     * 同一模块的节点才会互相做向量匹配，防止跨模块串台
     */
    private String module;

    /**
     * 节点正文，保留原文关键信息
     * 向量化的原始文本，也是 ImpactAgent/ConsistencyAgent 的输入
     */
    private String content;

    /**
     * reqType 确认状态
     * DRAFT = LLM 初判，待用户确认
     * CONFIRMED = 用户已确认，可参与路由
     * ParseAgent 输出后默认 DRAFT，不由 LLM 填写
     */
    @Builder.Default
    private String status = "DRAFT";
}
