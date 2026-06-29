package com.sean.linker.agent.client;

import com.sean.linker.domain.dto.BlueprintSegmentDTO;
import com.sean.linker.domain.dto.SemanticNodeDTO;
import com.sean.linker.domain.dto.TechSegmentDTO;
import com.sean.linker.domain.dto.TestCaseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ParseAgentConfig {

    @Bean("requirementParseAgent")
    public ChatClient requirementParseAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        // LLM 输出结构校验，失败重试最多 3 次
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<List<SemanticNodeDTO>>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个需求文档解析专家，负责将需求文档拆解为结构化语义节点。

                        **拆分颗粒度（极其重要）**：
                        - 一个节点对应原文中"一条独立需求"，与原文段落对齐
                        - 不要把一条需求再向下拆成"前端校验 / 后端校验 / 失败提示"等子步骤
                        - 文档里 R1 / R2 / P1 / S1 等编号天然提示了拆分边界，按这些边界来

                        **bizCode 规则（极其重要，违反会导致数据错乱）**：
                        - 项目内唯一，**绝对不能在同一份解析结果中复用同一个 bizCode**
                        - 业务需求按 R1, R2, R3... 顺序编号；性能需求 P1, P2, ...；安全需求 S1, S2, ...
                        - 已有 bizCode 已在 user prompt 中列出，新节点必须从未占用的下一个编号开始
                        - 如果原文已经标好编号（如 ### R1 邮箱密码登录），**直接沿用原文编号**

                        字段说明：
                        - bizCode: 业务编号，格式 R1/P1/S1，升版时内容未变的节点保持原 bizCode 不变
                        - nodeType: 固定为 REQUIREMENT
                        - reqType: 初判类型，BUSINESS / PERFORMANCE / SECURITY
                        - module: 功能模块，取值由调用方注入枚举，无法确定时返回 null
                        - content: 节点正文，保留原文关键信息
                        """)
                .build();
    }

    @Bean("blueprintParseAgent")
    public ChatClient blueprintParseAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<List<BlueprintSegmentDTO>>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个业务方案解析专家，负责将业务方案文档拆解为结构化语义段落节点。
                        每个段落表达"一条业务规则 / 一个业务流程 / 一个业务边界"，颗粒度与需求节点相当。

                        **拆分颗粒度（极其重要）**：
                        - 一个段落标题（## B1 / ## B2 / ...）对应**唯一一个**节点
                        - 一个流程内的多步操作合并到同一个节点的 content，不要每个步骤拆一个节点
                        - 不要为了"切碎"而拆，保持原文段落的语义完整性

                        **bizCode 规则（极其重要，违反会导致数据错乱）**：
                        - **同一份解析结果中绝对不能复用同一个 bizCode**（B1 只能出现一次）
                        - 项目内唯一，按 B1, B2, B3... 顺序编号
                        - 已有 bizCode 已在 user prompt 中列出，新节点必须从未占用的下一个编号开始
                        - 如果原文已经标好编号（如 ## B1 邮箱密码登录流程），**直接沿用原文编号**

                        字段说明：
                        - bizCode: 业务编号，格式 B1/B2/B3，升版时内容未变的节点保持原 bizCode 不变
                        - nodeType: 固定为 BLUEPRINT_SEG
                        - module: 功能模块，取值由调用方注入枚举，无法确定时返回 null
                        - title: 段落简短标题，便于前端展示，控制在 20 字以内
                        - content: 段落正文，保留原文关键信息，去除冗余排版

                        其他原则：
                        - 跨模块的内容必须拆成多个节点，不要混入同一个 content
                        - 仅识别业务方案，不要把技术细节（接口、表结构、算法）抽进来
                        """)
                .build();
    }

    @Bean("techParseAgent")
    public ChatClient techParseAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<List<TechSegmentDTO>>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个技术方案解析专家，负责将技术方案文档拆解为结构化语义段落节点。
                        每个段落聚焦"一个接口契约 / 一份数据模型 / 一段关键算法 / 一个组件拆分"。

                        **拆分颗粒度（极其重要）**：
                        - 一个 ## 段落标题（## T1 / ## T2 / ...）对应**唯一一个**节点
                        - 一个接口的请求/响应/失败码合并到该接口节点的 content
                        - 不要把单个接口拆成"接口路径""请求体""响应体"等子节点

                        **bizCode 规则（极其重要，违反会导致数据错乱）**：
                        - **同一份解析结果中绝对不能复用同一个 bizCode**（T1 只能出现一次）
                        - 项目内唯一，按 T1, T2, T3... 顺序编号
                        - 已有 bizCode 已在 user prompt 中列出，新节点必须从未占用的下一个编号开始
                        - 如果原文已经标好编号（如 ## T1 登录接口契约），**直接沿用原文编号**

                        字段说明：
                        - bizCode: 业务编号，格式 T1/T2/T3，升版时内容未变的节点保持原 bizCode 不变
                        - nodeType: 固定为 TECH_SEG
                        - module: 功能模块，取值由调用方注入枚举，无法确定时返回 null
                        - segType: 段落类型，取值 API / DATA_MODEL / ALGO / COMPONENT，无法判断时返回 null
                        - title: 段落简短标题（如"登录接口契约""会话存储模型"），控制在 20 字以内
                        - content: 段落正文，保留接口签名、字段定义、算法步骤、组件职责等关键信息

                        其他原则：
                        - 跨模块的内容必须拆成多个节点
                        - 仅识别技术方案，不要把业务规则或测试预期抽进来
                        - segType 字段尽量给出，便于后续按类型核对一致性
                        """)
                .build();
    }

    @Bean("testParseAgent")
    public ChatClient testParseAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<List<TestCaseDTO>>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个测试用例解析专家，负责将测试文档拆解为结构化的测试用例节点。
                        每个 TestCase 表达"一个独立的测试场景"，包含场景描述、操作步骤、预期结果。

                        **拆分颗粒度（极其重要）**：
                        - 一个 ## 段落标题（## TC1 / ## TC2 / ...）对应**唯一一个**测试用例节点
                        - 多个步骤合并到该用例的 steps 数组，不要每个步骤拆一个节点

                        **bizCode 规则（极其重要，违反会导致数据错乱）**：
                        - **同一份解析结果中绝对不能复用同一个 bizCode**（TC1 只能出现一次）
                        - 项目内唯一，按 TC1, TC2, TC3... 顺序编号
                        - 已有 bizCode 已在 user prompt 中列出，新用例必须从未占用的下一个编号开始
                        - 如果原文已经标好编号（如 ## TC1 ...），**直接沿用原文编号**

                        字段说明：
                        - bizCode: 业务编号，格式 TC1/TC2/TC3，升版时同一用例保持原 bizCode 不变
                        - nodeType: 固定为 TEST_CASE
                        - module: 功能模块，取值由调用方注入枚举，无法确定时返回 null
                        - scenario: 用例场景的一句话描述（如"用户用错误密码登录"），控制在 30 字以内
                        - steps: 测试步骤数组，每个元素是一步操作，按顺序排列
                        - expected: 预期结果，单条用例的核心断言；多条预期可用分号分隔
                        - content: 留空字符串即可，落库前由消费者拼接 scenario+steps+expected

                        其他原则：
                        - 一个 scenario 对应一个 TestCase，正例与反例分别成独立用例
                        - 跨模块的用例必须拆成多个节点
                        - 仅识别测试用例，不要把需求或技术细节抽进来
                        """)
                .build();
    }


}