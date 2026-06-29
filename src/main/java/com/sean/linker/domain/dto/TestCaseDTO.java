package com.sean.linker.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseDTO {

    /**
     * 业务编号，如 TC1、TC2
     * TestParseAgent 输出的对齐键，升版时同一用例保持原 bizCode 不变
     */
    private String bizCode;

    /**
     * 节点类型，固定 TEST_CASE
     */
    private String nodeType;

    /**
     * 所属功能模块，如"登录"/"支付"
     * 与 REQUIREMENT 的 module 共用同一份枚举，关联候选检索的结构化锚点
     * LLM 无法判断时返回 null
     */
    private String module;

    /**
     * 用例场景描述，如"用户用错误密码登录"
     */
    private String scenario;

    /**
     * 测试步骤，有序列表
     * 每一项是一步操作，便于前端列表展示
     */
    private List<String> steps;

    /**
     * 预期结果，用例的核心断言
     * ConsistencyAgent 会用这一字段判断用例是否覆盖了上游需求的关键意图
     */
    private String expected;

    /**
     * 落库时拼接 scenario + steps + expected 作为节点 content 用于向量化
     * 该字段由 LLM 输出时可为空，落库前由消费者拼接填充
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