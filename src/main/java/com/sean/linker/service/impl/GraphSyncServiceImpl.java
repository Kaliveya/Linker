package com.sean.linker.service.impl;

import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.repository.SemanticGraphNodeRepository;
import com.sean.linker.service.GraphSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSyncServiceImpl implements GraphSyncService {

    private final SemanticGraphNodeRepository semanticGraphNodeRepository;

    @Override
    public void upsertNode(SemanticNodeEntity node) {
        try {
            semanticGraphNodeRepository.upsertNode(
                    node.getId(),
                    node.getProjectId(),
                    node.getNodeType(),
                    node.getReqType(),
                    node.getModule(),
                    node.getOwnerId(),
                    node.getStatus()
            );
            log.info("[GraphSync] 节点投影同步成功 nodeId={}", node.getId());
        } catch (Exception e) {
            // demo 阶段简化：Neo4j 同步失败仅记日志，不阻塞 PG 主流程
            // 生产阶段应该改成 graph_sync_outbox 异步重试
            log.error("[GraphSync] 节点投影同步失败 nodeId={}, 已记日志待人工补偿",
                    node.getId(), e);
        }
    }
}