package com.sean.linker.service.impl;

import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.repository.SemanticGraphNodeRepository;
import com.sean.linker.repository.SemanticGraphRelationRepository;
import com.sean.linker.service.GraphSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSyncServiceImpl implements GraphSyncService {

    private final SemanticGraphNodeRepository semanticGraphNodeRepository;
    private final SemanticGraphRelationRepository semanticGraphRelationRepository;

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

    @Override
    public boolean upsertEdge(NodeRelationEntity rel) {
        try {
            switch (rel.getRelationType()) {
                case "REFINE" -> semanticGraphRelationRepository.upsertRefine(
                        rel.getFromNodeId(), rel.getToNodeId(), rel.getConfidence());
                case "DERIVE" -> semanticGraphRelationRepository.upsertDerive(
                        rel.getFromNodeId(), rel.getToNodeId(), rel.getConfidence());
                case "IMPLEMENT" -> semanticGraphRelationRepository.upsertImplement(
                        rel.getFromNodeId(), rel.getToNodeId(), rel.getConfidence());
                default -> {
                    log.warn("[GraphSync] 未知关系类型 relId={}, type={}",
                            rel.getId(), rel.getRelationType());
                    return false;
                }
            }
            log.info("[GraphSync] 边同步成功 relId={} {}-[:{}]->{}",
                    rel.getId(), rel.getFromNodeId(), rel.getRelationType(), rel.getToNodeId());
            return true;
        } catch (Exception e) {
            log.error("[GraphSync] 边同步失败 relId={}, 已记日志待人工补偿", rel.getId(), e);
            return false;
        }
    }

    @Override
    public boolean deleteEdge(NodeRelationEntity rel) {
        try {
            switch (rel.getRelationType()) {
                case "REFINE" -> semanticGraphRelationRepository.deleteRefine(
                        rel.getFromNodeId(), rel.getToNodeId());
                case "DERIVE" -> semanticGraphRelationRepository.deleteDerive(
                        rel.getFromNodeId(), rel.getToNodeId());
                case "IMPLEMENT" -> semanticGraphRelationRepository.deleteImplement(
                        rel.getFromNodeId(), rel.getToNodeId());
                default -> {
                    log.warn("[GraphSync] 未知关系类型 relId={}, type={}",
                            rel.getId(), rel.getRelationType());
                    return false;
                }
            }
            log.info("[GraphSync] 边删除成功 relId={} {}-[:{}]->{}",
                    rel.getId(), rel.getFromNodeId(), rel.getRelationType(), rel.getToNodeId());
            return true;
        } catch (Exception e) {
            log.error("[GraphSync] 边删除失败 relId={}, 已记日志待人工补偿", rel.getId(), e);
            return false;
        }
    }
}
