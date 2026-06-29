package com.sean.linker.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.sean.linker.agent.advisor.ModuleVocabularyAdvisor;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.ExistingResearch;
import com.sean.linker.domain.dto.BlueprintSegmentDTO;
import com.sean.linker.domain.dto.SemanticNodeDTO;
import com.sean.linker.domain.dto.TechSegmentDTO;
import com.sean.linker.domain.dto.TestCaseDTO;
import com.sean.linker.domain.entity.ArtifactDocEntity;
import com.sean.linker.domain.entity.ArtifactDocVersionEntity;
import com.sean.linker.domain.entity.ProjectModuleEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.ModuleVO;
import com.sean.linker.mapper.ArtifactDocMapper;
import com.sean.linker.mapper.ArtifactDocVersionMapper;
import com.sean.linker.mapper.ProjectModuleMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.RelationCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
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
    private final RelationCandidateService relationCandidateService;
    @Qualifier("requirementParseAgent")
    private final ChatClient requirementParseAgent;
    @Qualifier("blueprintParseAgent")
    private final ChatClient blueprintParseAgent;
    @Qualifier("techParseAgent")
    private final ChatClient techParseAgent;
    @Qualifier("testParseAgent")
    private final ChatClient testParseAgent;

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
                    analyzeBlueprintDoc(doc, version);
                    break;
                case ConstantStatic.TECH:
                    analyzeTechDoc(doc, version);
                    break;
                case ConstantStatic.TEST:
                    analyzeTestDoc(doc, version);
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
        // 查询 project_module 拿全部模块（code + name），后面注入 ParseAgent 用
        Long projectId = doc.getProjectId();
        List<ModuleVO> modules = loadProjectModules(projectId);

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
        List<SemanticNodeDTO> nodes = requirementParseAgent.prompt()
                .user(existingNodesPart + "请解析以下文档：\n" + docContent)
                .advisors(new ModuleVocabularyAdvisor(modules))
                .call()
                .entity(new ParameterizedTypeReference<List<SemanticNodeDTO>>() {});

        // 防御性 bizCode 去重：兜住 LLM 复用 / 重复输出已有节点
        Set<String> existingBizCodes = existingNodes.stream()
                .map(ExistingResearch::getBizCode).collect(Collectors.toSet());
        nodes = dedupBizCodes(nodes, existingBizCodes, SemanticNodeDTO::getBizCode, "RESEARCH");

        // 筛选出有效的 moduleCode，并创建 SemanticNodeEntity插入数据库
        Set<String> validCodes = modules.stream().map(ModuleVO::getCode).collect(Collectors.toSet());
        List<SemanticNodeEntity> entities = new ArrayList<>();
        Map<Long ,String> contents = new HashMap<>();
        for (SemanticNodeDTO node : nodes){
            SemanticNodeEntity entity = new SemanticNodeEntity();
            entity.setModuleStatus(ConstantStatic.MODULE_STATUS_CONFIRMED);
            if (node.getModule() == null || !validCodes.contains(node.getModule())) {
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

    /**
     * 蓝图方案流水线
     * @param doc
     * @param version
     */
    private void analyzeBlueprintDoc(ArtifactDocEntity doc, ArtifactDocVersionEntity version) {

        // 查询 project_module 拿全部模块（code + name）
        Long projectId = doc.getProjectId();
        List<ModuleVO> modules = loadProjectModules(projectId);

        // 查询semantic_node获取已有业务功能点
        List<ExistingResearch> existingNodes = semanticNodeMapper.selectList(
                        new LambdaQueryWrapper<SemanticNodeEntity>()
                                .eq(SemanticNodeEntity::getProjectId, projectId)
                                .eq(SemanticNodeEntity::getNodeType, ConstantStatic.BLUEPRINT_SEG))
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

        // 解析文档
        List<BlueprintSegmentDTO> nodes = blueprintParseAgent.prompt()
                .user(existingNodesPart + "请解析以下文档：\n" + docContent)
                .advisors(new ModuleVocabularyAdvisor(modules))
                .call()
                .entity(new ParameterizedTypeReference<List<BlueprintSegmentDTO>>() {});

        // 防御性 bizCode 去重
        Set<String> existingBizCodes = existingNodes.stream()
                .map(ExistingResearch::getBizCode).collect(Collectors.toSet());
        nodes = dedupBizCodes(nodes, existingBizCodes, BlueprintSegmentDTO::getBizCode, "BLUEPRINT");

        // 筛选出有效的 moduleCode，并创建 SemanticNodeEntity插入数据库
        Set<String> validCodes = modules.stream().map(ModuleVO::getCode).collect(Collectors.toSet());
        List<SemanticNodeEntity> entities = new ArrayList<>();
        Map<Long ,String> contents = new HashMap<>();
        for (BlueprintSegmentDTO node : nodes){
            SemanticNodeEntity entity = new SemanticNodeEntity();
            entity.setModuleStatus(ConstantStatic.MODULE_STATUS_CONFIRMED);
            if (node.getModule() == null || !validCodes.contains(node.getModule())) {
                node.setModule(null);
                entity.setModuleStatus(ConstantStatic.MODULE_STATUS_PENDING);
            }
            entity.setDocId(doc.getId());
            entity.setModule(node.getModule());
            entity.setBizCode(node.getBizCode());
            entity.setNodeType(ConstantStatic.BLUEPRINT_SEG);
            entity.setOwnerId(version.getAuthorId());
            entity.setContent(node.getContent());
            entity.setProjectId(doc.getProjectId());
            entity.setStatus(node.getStatus());
            entity.setReqTypeConfirmed(true);
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

        // 触发候选关联生成（系统命门 · 三重防御）
        // 只对 module 已确认的节点触发；PENDING 节点等用户在前端确认 module 后由 NodeConfirmService 补触发
        for (SemanticNodeEntity entity : entities) {
            if (ConstantStatic.MODULE_STATUS_CONFIRMED.equals(entity.getModuleStatus())) {
                try {
                    relationCandidateService.suggestRelations(entity.getId());
                } catch (Exception e) {
                    log.error("[Pipeline] 候选关联生成失败 nodeId={}", entity.getId(), e);
                }
            }
        }
    }

    /**
     * 技术方案流水线
     * 与 blueprint 同套路：解析→落库→embedding→触发候选关联
     * 上游锚点由 RelationCandidateService 自动按 nodeType 切换为 BLUEPRINT_SEG
     */
    private void analyzeTechDoc(ArtifactDocEntity doc, ArtifactDocVersionEntity version) {
        Long projectId = doc.getProjectId();
        List<ModuleVO> modules = loadProjectModules(projectId);

        List<ExistingResearch> existingNodes = semanticNodeMapper.selectList(
                        new LambdaQueryWrapper<SemanticNodeEntity>()
                                .eq(SemanticNodeEntity::getProjectId, projectId)
                                .eq(SemanticNodeEntity::getNodeType, ConstantStatic.TECH_SEG))
                .stream()
                .map(node -> new ExistingResearch(node.getBizCode(), node.getContent()))
                .toList();

        String docContent;
        try {
            docContent = Files.readString(Path.of(version.getFilePath()));
        } catch (IOException e) {
            log.error("[Pipeline] docId={} 获取文档全文内容失败", doc.getId(), e);
            throw new RuntimeException("读取文件失败 docId=" + doc.getId(), e);
        }

        String existingNodesPart = existingNodes.isEmpty() ? "" : """
        已有节点（bizCode 已占用，新节点不得重复）：
        %s

        """.formatted(existingNodes.stream()
                .map(n -> n.getBizCode() + ": " + n.getContent())
                .collect(Collectors.joining("\n")));

        List<TechSegmentDTO> nodes = techParseAgent.prompt()
                .user(existingNodesPart + "请解析以下文档：\n" + docContent)
                .advisors(new ModuleVocabularyAdvisor(modules))
                .call()
                .entity(new ParameterizedTypeReference<List<TechSegmentDTO>>() {});

        Set<String> existingBizCodes = existingNodes.stream()
                .map(ExistingResearch::getBizCode).collect(Collectors.toSet());
        nodes = dedupBizCodes(nodes, existingBizCodes, TechSegmentDTO::getBizCode, "TECH");

        Set<String> validCodes = modules.stream().map(ModuleVO::getCode).collect(Collectors.toSet());
        List<SemanticNodeEntity> entities = new ArrayList<>();
        Map<Long, String> contents = new HashMap<>();
        for (TechSegmentDTO node : nodes) {
            SemanticNodeEntity entity = new SemanticNodeEntity();
            entity.setModuleStatus(ConstantStatic.MODULE_STATUS_CONFIRMED);
            if (node.getModule() == null || !validCodes.contains(node.getModule())) {
                node.setModule(null);
                entity.setModuleStatus(ConstantStatic.MODULE_STATUS_PENDING);
            }
            // segType 拼到 content 前缀，避免动表结构（如 [API] 登录接口契约：...）
            String content = node.getSegType() != null
                    ? "[" + node.getSegType() + "] " + node.getContent()
                    : node.getContent();
            entity.setDocId(doc.getId());
            entity.setModule(node.getModule());
            entity.setBizCode(node.getBizCode());
            entity.setNodeType(ConstantStatic.TECH_SEG);
            entity.setOwnerId(version.getAuthorId());
            entity.setContent(content);
            entity.setProjectId(doc.getProjectId());
            entity.setStatus(node.getStatus());
            entity.setReqTypeConfirmed(true);
            entity.setVersion(1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entities.add(entity);
            semanticNodeMapper.insert(entity);
            contents.put(entity.getId(), entity.getContent());
        }

        for (Map.Entry<Long, String> entry : contents.entrySet()) {
            String embeddings = Arrays.toString(embeddingModel.embed(entry.getValue()))
                    .replace(", ", ",");
            semanticNodeMapper.updateEmbedding(entry.getKey(), embeddings);
        }

        for (SemanticNodeEntity entity : entities) {
            if (ConstantStatic.MODULE_STATUS_CONFIRMED.equals(entity.getModuleStatus())) {
                try {
                    relationCandidateService.suggestRelations(entity.getId());
                } catch (Exception e) {
                    log.error("[Pipeline] 候选关联生成失败 nodeId={}", entity.getId(), e);
                }
            }
        }
    }

    /**
     * 测试用例流水线
     * 差异点：TestCaseDTO 有结构化的 scenario/steps/expected，落库前拼成 content 用于 embedding
     */
    private void analyzeTestDoc(ArtifactDocEntity doc, ArtifactDocVersionEntity version) {
        Long projectId = doc.getProjectId();
        List<ModuleVO> modules = loadProjectModules(projectId);

        List<ExistingResearch> existingNodes = semanticNodeMapper.selectList(
                        new LambdaQueryWrapper<SemanticNodeEntity>()
                                .eq(SemanticNodeEntity::getProjectId, projectId)
                                .eq(SemanticNodeEntity::getNodeType, ConstantStatic.TEST_CASE))
                .stream()
                .map(node -> new ExistingResearch(node.getBizCode(), node.getContent()))
                .toList();

        String docContent;
        try {
            docContent = Files.readString(Path.of(version.getFilePath()));
        } catch (IOException e) {
            log.error("[Pipeline] docId={} 获取文档全文内容失败", doc.getId(), e);
            throw new RuntimeException("读取文件失败 docId=" + doc.getId(), e);
        }

        String existingNodesPart = existingNodes.isEmpty() ? "" : """
        已有节点（bizCode 已占用，新节点不得重复）：
        %s

        """.formatted(existingNodes.stream()
                .map(n -> n.getBizCode() + ": " + n.getContent())
                .collect(Collectors.joining("\n")));

        List<TestCaseDTO> nodes = testParseAgent.prompt()
                .user(existingNodesPart + "请解析以下文档：\n" + docContent)
                .advisors(new ModuleVocabularyAdvisor(modules))
                .call()
                .entity(new ParameterizedTypeReference<List<TestCaseDTO>>() {});

        Set<String> existingBizCodes = existingNodes.stream()
                .map(ExistingResearch::getBizCode).collect(Collectors.toSet());
        nodes = dedupBizCodes(nodes, existingBizCodes, TestCaseDTO::getBizCode, "TEST");

        Set<String> validCodes = modules.stream().map(ModuleVO::getCode).collect(Collectors.toSet());
        List<SemanticNodeEntity> entities = new ArrayList<>();
        Map<Long, String> contents = new HashMap<>();
        for (TestCaseDTO node : nodes) {
            SemanticNodeEntity entity = new SemanticNodeEntity();
            entity.setModuleStatus(ConstantStatic.MODULE_STATUS_CONFIRMED);
            if (node.getModule() == null || !validCodes.contains(node.getModule())) {
                node.setModule(null);
                entity.setModuleStatus(ConstantStatic.MODULE_STATUS_PENDING);
            }
            // 拼接结构化字段为 content（用于 embedding + 前端展示）
            String steps = node.getSteps() == null
                    ? ""
                    : String.join("\n- ", node.getSteps());
            String content = """
                    场景：%s
                    步骤：
                    - %s
                    预期：%s
                    """.formatted(
                    node.getScenario() == null ? "" : node.getScenario(),
                    steps,
                    node.getExpected() == null ? "" : node.getExpected());

            entity.setDocId(doc.getId());
            entity.setModule(node.getModule());
            entity.setBizCode(node.getBizCode());
            entity.setNodeType(ConstantStatic.TEST_CASE);
            entity.setOwnerId(version.getAuthorId());
            entity.setContent(content);
            entity.setProjectId(doc.getProjectId());
            entity.setStatus(node.getStatus());
            entity.setReqTypeConfirmed(true);
            entity.setVersion(1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entities.add(entity);
            semanticNodeMapper.insert(entity);
            contents.put(entity.getId(), entity.getContent());
        }

        for (Map.Entry<Long, String> entry : contents.entrySet()) {
            String embeddings = Arrays.toString(embeddingModel.embed(entry.getValue()))
                    .replace(", ", ",");
            semanticNodeMapper.updateEmbedding(entry.getKey(), embeddings);
        }

        for (SemanticNodeEntity entity : entities) {
            if (ConstantStatic.MODULE_STATUS_CONFIRMED.equals(entity.getModuleStatus())) {
                try {
                    relationCandidateService.suggestRelations(entity.getId());
                } catch (Exception e) {
                    log.error("[Pipeline] 候选关联生成失败 nodeId={}", entity.getId(), e);
                }
            }
        }
    }

    // ───────────────────────── 公共辅助 ─────────────────────────

    /** 加载项目模块列表，含 code + name，用于注入 ParseAgent 的闭域词表 */
    private List<ModuleVO> loadProjectModules(Long projectId) {
        return projectModuleMapper.selectList(
                        new LambdaQueryWrapper<ProjectModuleEntity>()
                                .eq(ProjectModuleEntity::getProjectId, projectId))
                .stream()
                .map(m -> ModuleVO.builder()
                        .id(m.getId())
                        .code(m.getCode())
                        .name(m.getName())
                        .build())
                .toList();
    }

    /**
     * 防御性 bizCode 去重，保证 PG 端 (project_id, node_type, biz_code) 唯一。
     * LLM 即便 prompt 写得再严也可能：① 同一份输出复用相同 bizCode，② 重复输出已有节点的 bizCode。
     * 这一步只是兜底（参数 nodes 是 mutable list 时也安全），所有过滤都打日志方便排查。
     */
    private <T> List<T> dedupBizCodes(List<T> nodes,
                                      Set<String> existingBizCodes,
                                      Function<T, String> bizCodeOf,
                                      String docTypeLabel) {
        Set<String> seenInBatch = new HashSet<>();
        List<T> result = new ArrayList<>();
        for (T n : nodes) {
            String bc = bizCodeOf.apply(n);
            if (bc == null || bc.isBlank()) {
                log.warn("[Pipeline-{}] LLM 返回节点缺 bizCode，丢弃", docTypeLabel);
                continue;
            }
            if (existingBizCodes.contains(bc)) {
                log.info("[Pipeline-{}] bizCode={} 已存在，跳过新增", docTypeLabel, bc);
                continue;
            }
            if (!seenInBatch.add(bc)) {
                log.warn("[Pipeline-{}] LLM 同批复用 bizCode={}，仅保留首条", docTypeLabel, bc);
                continue;
            }
            result.add(n);
        }
        return result;
    }

}
