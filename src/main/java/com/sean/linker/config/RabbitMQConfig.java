package com.sean.linker.config;

import com.sean.linker.common.ConstantStatic;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange pipelineExchange() {
        return new DirectExchange(ConstantStatic.PIPELINE_EXCHANGE, true, false);
    }

    @Bean
    public Queue queuePipelineUpload() {
        return QueueBuilder.durable(ConstantStatic.QUEUE_PIPELINE_UPLOAD).build();
    }

    @Bean
    public Queue queuePipelineVersion() {
        return QueueBuilder.durable(ConstantStatic.QUEUE_PIPELINE_VERSION).build();
    }

    @Bean
    public Binding bindingUpload(DirectExchange pipelineExchange, Queue queuePipelineUpload) {
        return BindingBuilder.bind(queuePipelineUpload)
                .to(pipelineExchange)
                .with(ConstantStatic.ROUTING_DOC_UPLOAD);
    }

    @Bean
    public Binding bindingVersion(DirectExchange pipelineExchange, Queue queuePipelineVersion) {
        return BindingBuilder.bind(queuePipelineVersion)
                .to(pipelineExchange)
                .with(ConstantStatic.ROUTING_DOC_VERSION);
    }
}
