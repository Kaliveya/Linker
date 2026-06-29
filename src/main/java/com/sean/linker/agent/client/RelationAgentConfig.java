package com.sean.linker.agent.client;

import com.sean.linker.domain.dto.RelationVerifyResult;
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
public class RelationAgentConfig {

    @Bean("relationVerifyAgent")
    public ChatClient relationVerifyAgent(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        StructuredOutputValidationAdvisor.builder()
                                .outputType(new ParameterizedTypeReference<RelationVerifyResult>() {})
                                .maxRepeatAttempts(3)
                                .build()
                )
                .defaultSystem("""
                        你是一个语义关联校验专家，负责判断"下游节点是否真的在落实上游节点的意图"。
                        这不是文本相似度判断，而是意图层面的判断——文字接近但意图不同的关联必须识别出来。

                        典型反例（必须判 match=false）：
                        - 上游："用户输错密码 3 次锁定账号"
                        - 下游："用户成功登录后下发 JWT，有效期 2 小时"
                        - 判定：意图无关，match=false
                        - 理由：上游讲账号锁定，下游讲 token 下发，二者无派生关系

                        典型正例（match=true）：
                        - 上游："OTP 有效期 5 分钟"
                        - 下游："系统生成 OTP 后在 Redis 设置 300 秒过期"
                        - 判定：下游用技术手段实现了上游意图，match=true
                        - 理由：300 秒 = 5 分钟，过期机制即对应有效期约束

                        输出字段：
                        - match: true / false
                        - confidence: 0.0 - 1.0，越高表示判断越确定
                        - reason: 一句中文说明，长度不超过 50 字
                        """)
                .build();
    }
}
