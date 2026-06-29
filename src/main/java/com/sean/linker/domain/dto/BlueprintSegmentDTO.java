package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlueprintSegmentDTO {

    /**
     * 业务编号，如 B1、B2
     * BlueprintParseAgent 输出的对齐键，升版时内容未变的段落保持原 bizCode 不变
     */
    private String bizCode;

    /**
     * 节点类型，固定 BLUEPRINT_SEG
     */
    private String nodeType;

    /**
     * 所属功能模块，如"登录"/"支付"
     * 与 REQUIREMENT 的 module 共用同一份枚举，作为关联候选检索的锚点
     * LLM 无法判断时返回 null，进入"模糊待人工确认"分支
     */
    private String module;

    /**
     * 段落标题，便于前端列表展示与确认
     */
    private String title;

    /**
     * 段落正文，保留业务规则/流程/边界描述
     * 向量化的原始文本，也是 RelationVerifyAgent / ConsistencyAgent 的输入
     */
    private String content;

    /**
     * 确认状态
     * DRAFT = LLM 初判，待用户确认
     * CONFIRMED = 用户已确认，可参与关联候选生成
     */
    @Builder.Default
    private String status = "DRAFT";
}