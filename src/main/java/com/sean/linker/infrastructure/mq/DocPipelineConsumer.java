package com.sean.linker.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.sean.linker.agent.advisor.ModuleVocabularyAdvisor;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.ExistingResearch;
import com.sean.linker.domain.dto.SemanticNodeDTO;
import com.sean.linker.domain.entity.ArtifactDocEntity;
import com.sean.linker.domain.entity.ArtifactDocVersionEntity;
import com.sean.linker.domain.entity.ProjectModuleEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.mapper.ArtifactDocMapper;
import com.sean.linker.mapper.ArtifactDocVersionMapper;
import com.sean.linker.mapper.ProjectModuleMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocPipelineConsumer {

    private final ArtifactDocMapper artifactDocMapper;
    private final ArtifactDocVersionMapper artifactDocVersionMapper;
    private final ProjectModuleMapper projectModuleMapper;
    private final SemanticNodeMapper semanticNodeMapper;
    private final EmbeddingModel embeddingModel;

    private final ChatClient parseAgentChatClient;

    @RabbitListener(queues = ConstantStatic.QUEUE_PIPELINE_UPLOAD)
    public void handleDocUpload(Long docId, Message message, Channel channel) throws IOException {
        log.info("[Pipeline] 收到文档上传事件，docId={}", docId);
        try {
            // 1. 查 artifact_doc → 拿到 projectId、docType
            ArtifactDocEntity doc = artifactDocMapper.selectById(docId);

            // 2. 查 artifact_doc_version 最新版 → 拿到 filePath
            ArtifactDocVersionEntity version = artifactDocVersionMapper.selectOne(
                    new LambdaQueryWrapper<ArtifactDocVersionEntity>()
                            .eq(ArtifactDocVersionEntity::getDocId, docId)
                            .orderByDesc(ArtifactDocVersionEntity::getVersion)
                            .last("LIMIT 1")
            );

            // 3. 按 docType 分流
            switch (doc.getDocType()) {
                case ConstantStatic.RESEARCH:
                    analyzeResearchDoc(doc, version);
                    break;
                case ConstantStatic.BLUEPRINT:
                case ConstantStatic.TECH:
                case ConstantStatic.TEST:
                    break;
            }

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.info("[Pipeline] docId={} 处理完成", docId);

        } catch (Exception e) {
            // 标记"待人工处理"，ack 掉消息避免阻塞队列
            log.error("[Pipeline] docId={} 流水线失败，已标记待人工处理", docId, e);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

    /**
     * 需求文档流水线
     * @param doc
     * @param version
     */
    private void analyzeResearchDoc(ArtifactDocEntity doc, ArtifactDocVersionEntity version) {
        // 查询project_module获取全部module code
        Long projectId = doc.getProjectId();
        List<String> moduleCodes = projectModuleMapper.selectList(
                new LambdaQueryWrapper<ProjectModuleEntity>()
                        .eq(ProjectModuleEntity::getProjectId, projectId))
                .stream().map(ProjectModuleEntity::getCode).toList();

        // 查询semantic_node获取已有需求
        List<ExistingResearch> existingNodes = semanticNodeMapper.selectList(
                        new LambdaQueryWrapper<SemanticNodeEntity>()
                                .eq(SemanticNodeEntity::getProjectId, projectId)
                                .eq(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT))
                .stream()
                .map(node -> new ExistingResearch(node.getBizCode(), node.getContent()))
                .toList();

        // 获取上传文档全文内容
        String docContent = "";
        try {
            docContent = Files.readString(Path.of(version.getFilePath()));
        } catch (IOException e) {
            log.error("[Pipeline] docId={} 获取文档全文内容失败", doc.getId(), e);
            throw new RuntimeException("读取文件失败 docId=" + doc.getId(), e);
        }

        // 拼接已有节点（首次上传时 existingNodes 为空列表，这段文字不会出现）
        String existingNodesPart = existingNodes.isEmpty() ? "" : """
        已有节点（bizCode 已占用，新节点不得重复）：
        %s
        
        """.formatted(existingNodes.stream()
                .map(n -> n.getBizCode() + ": " + n.getContent())
                .collect(Collectors.joining("\n")));
        // 调用 ParseAgent, 解析文档
        List<SemanticNodeDTO> nodes = parseAgentChatClient.prompt()
                .user(existingNodesPart + "请解析以下文档：\n" + docContent)
                .advisors(new ModuleVocabularyAdvisor(moduleCodes))
                .call()
                .entity(new ParameterizedTypeReference<List<SemanticNodeDTO>>() {});

        // 筛选出有效的 moduleCode，并创建 SemanticNodeEntity插入数据库
        Set<String> validCodes = new HashSet<>(moduleCodes);
        List<SemanticNodeEntity> entities = new ArrayList<>();
        Map<Long ,String> contents = new HashMap<>();
        for (SemanticNodeDTO node : nodes){
            SemanticNodeEntity entity = new SemanticNodeEntity();
            entity.setModuleStatus(ConstantStatic.MODULE_STATUS_CONFIRMED);
            if (node.getModule() != null && !validCodes.contains(node.getModule())){
                node.setModule(null);
                entity.setModuleStatus(ConstantStatic.MODULE_STATUS_PENDING);
            }
            entity.setDocId(doc.getId());
            entity.setModule(node.getModule());
            entity.setBizCode(node.getBizCode());
            entity.setNodeType(ConstantStatic.REQUIREMENT);
            entity.setOwnerId(version.getAuthorId());
            entity.setContent(node.getContent());
            entity.setProjectId(doc.getProjectId());
            entity.setReqType(node.getReqType());
            entity.setStatus(node.getStatus());
            entity.setReqTypeConfirmed(false);
            entity.setVersion(1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entities.add(entity);
            semanticNodeMapper.insert(entity);
            contents.put(entity.getId(), entity.getContent());
        }

        for (Map.Entry<Long, String> entry : contents.entrySet()) {
            Long id = entry.getKey();
            String content = entry.getValue();

            String embeddings = Arrays.toString(embeddingModel.embed(content))
                    .replace(", ", ",");
            semanticNodeMapper.updateEmbedding(id, embeddings);

        }


    }

}
