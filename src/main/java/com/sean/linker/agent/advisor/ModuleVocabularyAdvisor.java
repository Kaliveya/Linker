package com.sean.linker.agent.advisor;

import com.fasterxml.jackson.databind.JavaType;
import com.sean.linker.common.ConstantStatic;
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

@Slf4j
@RequiredArgsConstructor
public class ModuleVocabularyAdvisor implements BaseAdvisor {

    private final List<String> moduleCodes;

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

        String moduleConstraint = "\n\n可选的 module 枚举（只能从以下选择，否则返回 null）："
                + String.join(", ", moduleCodes);

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
