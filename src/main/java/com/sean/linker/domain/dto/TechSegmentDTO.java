package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechSegmentDTO {

    /**
     * 业务编号，如 T1、T2
     * TechParseAgent 输出的对齐键，升版时内容未变的段落保持原 bizCode 不变
     */
    private String bizCode;

    /**
     * 节点类型，固定 TECH_SEG
     */
    private String nodeType;

    /**
     * 所属功能模块，如"登录"/"支付"
     * 与 REQUIREMENT 的 module 共用同一份枚举，关联候选检索的结构化锚点
     * LLM 无法判断时返回 null
     */
    private String module;

    /**
     * 技术段落类型，便于前端分类展示
     * API / DATA_MODEL / ALGO / COMPONENT
     * LLM 给出，可为 null
     */
    private String segType;

    /**
     * 段落标题，如"登录接口契约""会话存储模型"
     */
    private String title;

    /**
     * 段落正文，保留接口契约/数据模型/关键算法/组件拆分的原始描述
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