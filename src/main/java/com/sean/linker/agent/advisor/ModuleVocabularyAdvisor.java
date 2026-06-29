package com.sean.linker.agent.advisor;

import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.vo.ModuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 注入项目模块闭域词表到 system prompt。
 * 旧版本只注 code，LLM 区分"LOGIN vs ACCOUNT"基本靠猜；
 * 现在带上模块中文名 + 归类规则，LLM 能据此把"找回密码"归到 ACCOUNT 而不是直接看到"密码"二字归到 LOGIN。
 */
@Slf4j
@RequiredArgsConstructor
public class ModuleVocabularyAdvisor implements BaseAdvisor {

    private final List<ModuleVO> modules;

    @Override
    public int getOrder() {
        return ConstantStatic.MODULE_VALIDATION_ADVISOR_ORDER;
    }

    @Override
    public String getName() {
        return "ModuleVocabularyAdvisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        List<Message> messages = new ArrayList<>(request.prompt().getInstructions());

        String moduleList = modules.stream()
                .map(m -> "  - " + m.getCode() + "：" + m.getName())
                .collect(Collectors.joining("\n"));

        String moduleConstraint = """


                可选 module 枚举（闭域，只能从下列选择，**严格按业务语义归类**，找不到对应模块时返回 null）：
                %s

                归类要求：
                - 仔细看模块的中文名称，按"内容主旨"归类，不要只看字面关键词
                - 同一文档不同段落经常分属不同模块，必须分别判断
                - 例如"找回密码 / 重置密码 / 邮件 token"通常属于账户类模块，不是登录模块
                - 拿不准就返回 null，不要硬塞
                """.formatted(moduleList);

        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage sm) {
                messages.set(i, new SystemMessage(sm.getText() + moduleConstraint));
                break;
            }
        }

        Prompt newPrompt = new Prompt(messages, request.prompt().getOptions());
        return request.mutate().prompt(newPrompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }
}
