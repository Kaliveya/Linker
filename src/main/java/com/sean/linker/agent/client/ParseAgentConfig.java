package com.sean.linker.agent.client;

import com.sean.linker.domain.dto.SemanticNodeDTO;
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

    @Bean("parseAgentChatClient")
    public ChatClient parseAgentChatClient(ChatClient.Builder builder) {
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

                        字段说明：
                        - bizCode: 业务编号，格式 R1/P1/S1，升版时内容未变的节点保持原 bizCode 不变
                        - nodeType: 固定为 REQUIREMENT
                        - reqType: 初判类型，BUSINESS / PERFORMANCE / SECURITY
                        - module: 功能模块，取值由调用方注入枚举，无法确定时返回 null
                        - content: 节点正文，保留原文关键信息
                        """)
                .build();
    }
}