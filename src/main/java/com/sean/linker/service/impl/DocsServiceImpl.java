package com.sean.linker.service.impl;

import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.dto.ImportDocsDTO;
import com.sean.linker.domain.entity.ArtifactDocEntity;
import com.sean.linker.domain.entity.ArtifactDocVersionEntity;
import com.sean.linker.mapper.ArtifactDocMapper;
import com.sean.linker.mapper.ArtifactDocVersionMapper;
import com.sean.linker.service.DocsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocsServiceImpl implements DocsService {

    private final ArtifactDocMapper artifactDocMapper;
    private final ArtifactDocVersionMapper artifactDocVersionMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public Long importDocs(Long projectId, ImportDocsDTO importDocsDTO) {
        // 1. 校验文件格式
        if (!importDocsDTO.getFileUrl().endsWith(".md")) {
            throw new IllegalArgumentException("本系统目前仅支持markdown文档，请重试");
        }

        // 2. 校验 docType 合法性（不合法直接抛，不进后续流程）
        switch (importDocsDTO.getDocType()) {
            case ConstantStatic.RESEARCH:
            case ConstantStatic.BLUEPRINT:
            case ConstantStatic.TECH:
            case ConstantStatic.TEST:
                break;
            default:
                throw new IllegalArgumentException("请选择正确的文档类型");
        }

        // 3. 模拟文件落盘（demo 阶段直接使用本地路径，无需上传）
        log.info("--------模拟上传文件服务器--------");

        // 4. artifact_doc 持久化
        String title = importDocsDTO.getFileUrl().substring(
                importDocsDTO.getFileUrl().lastIndexOf('/') + 1,
                importDocsDTO.getFileUrl().lastIndexOf('.')
        );
        ArtifactDocEntity artifactDocEntity = ArtifactDocEntity.builder()
                .projectId(projectId)
                .docType(importDocsDTO.getDocType())
                .currentVer(1)
                .title(title)
                .createdAt(LocalDateTime.now())
                .build();
        artifactDocMapper.insert(artifactDocEntity);

        // 5. artifact_doc_version 持久化（filePath 供消费者读文件用）
        ArtifactDocVersionEntity artifactDocVersionEntity = ArtifactDocVersionEntity.builder()
                .docId(artifactDocEntity.getId())
                .version(1)
                .filePath(importDocsDTO.getFileUrl())
                .changeNote("初始版本")
                .authorId(importDocsDTO.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
        artifactDocVersionMapper.insert(artifactDocVersionEntity);

        // 6. 发布到 MQ，触发异步解析流水线（持久化完成后才能拿到 docId）
        switch (importDocsDTO.getDocType()) {
            case ConstantStatic.RESEARCH:
                // 需求调研文档：触发 ParseAgent 解析流水线
                rabbitTemplate.convertAndSend(
                        ConstantStatic.PIPELINE_EXCHANGE,
                        ConstantStatic.ROUTING_DOC_UPLOAD,
                        artifactDocEntity.getId());
                break;
            case ConstantStatic.BLUEPRINT:
                rabbitTemplate.convertAndSend(
                        ConstantStatic.PIPELINE_EXCHANGE,
                        ConstantStatic.ROUTING_DOC_UPLOAD,
                        artifactDocEntity.getId());
                break;
            case ConstantStatic.TECH:
                break;
            case ConstantStatic.TEST:
                break;
        }

        return artifactDocEntity.getId();
    }
}
