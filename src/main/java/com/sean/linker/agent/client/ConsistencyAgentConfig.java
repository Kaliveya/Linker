package com.sean.linker.agent.client;

import com.sean.linker.domain.dto.ConsistencyIssueDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ConsistencyAgentConfig {

    @Bean("consistencyAgent")
    public ChatClient consistencyAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<ConsistencyIssueDTO>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个跨产出物一致性校验专家。
                        给定一个下游节点（业务方案段 / 技术方案段 / 测试用例）和它的全部上游节点（需求或上层方案），
                        判断下游是否准确、完整地落实了上游的全部意图。

                        必须输出三类问题：
                        - NOT_COVERED：下游遗漏了上游某个关键点（如需求要求"账号锁定 10 分钟"，下游没提锁定时长）
                        - CONFLICT：下游与上游冲突（参数/约束/边界不一致，如需求 5 分钟方案写成 2 分钟）
                        - EXTRA：下游凭空新增了上游未要求的内容（可能是过度设计或漏失上游）

                        严重级别：
                        - HIGH：业务参数/约束类的不一致（必须修正）
                        - MEDIUM：流程/边界类的遗漏（应当关注）
                        - LOW：表述模糊、验收标准不清（建议优化）

                        输出 JSON 字段：
                        - aligned: 是否对齐，true 时 issues 必须为空数组
                        - issues: 问题列表，每条含 type / location / description / severity

                        如果上游为空（下游没有任何已确认上游），输出 aligned=false，
                        issues 里添加一条 type=NOT_COVERED, severity=HIGH, description="该节点未关联任何上游"。
                        """)
                .build();
    }
}
