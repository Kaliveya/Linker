package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.RelationSuggestionVO;
import com.sean.linker.mapper.NodeRelationMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.GraphSyncService;
import com.sean.linker.service.RelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private final NodeRelationMapper nodeRelationMapper;
    private final SemanticNodeMapper semanticNodeMapper;
    private final GraphSyncService graphSyncService;

    @Override
    public List<RelationSuggestionVO> listRelations(Long projectId, Boolean confirmed) {
        LambdaQueryWrapper<NodeRelationEntity> qw = new LambdaQueryWrapper<NodeRelationEntity>()
                .eq(NodeRelationEntity::getProjectId, projectId)
                .orderByDesc(NodeRelationEntity::getCreatedAt);
        if (confirmed != null) {
            qw.eq(NodeRelationEntity::getConfirmed, confirmed);
        }
        List<NodeRelationEntity> rels = nodeRelationMapper.selectList(qw);
        if (rels.isEmpty()) return List.of();

        // 一次拉齐所有用到的节点
        Set<Long> nodeIds = new HashSet<>();
        for (NodeRelationEntity r : rels) {
            nodeIds.add(r.getFromNodeId());
            nodeIds.add(r.getToNodeId());
        }
        Map<Long, SemanticNodeEntity> nodeMap = semanticNodeMapper.selectBatchIds(nodeIds)
                .stream().collect(Collectors.toMap(SemanticNodeEntity::getId, Function.identity()));

        return rels.stream().map(r -> {
            SemanticNodeEntity from = nodeMap.get(r.getFromNodeId());
            SemanticNodeEntity to = nodeMap.get(r.getToNodeId());
            return RelationSuggestionVO.builder()
                    .id(r.getId())
                    .fromNodeId(r.getFromNodeId())
                    .fromBizCode(from != null ? from.getBizCode() : null)
                    .fromContent(from != null ? from.getContent() : null)
                    .fromNodeType(from != null ? from.getNodeType() : null)
                    .toNodeId(r.getToNodeId())
                    .toBizCode(to != null ? to.getBizCode() : null)
                    .toContent(to != null ? to.getContent() : null)
                    .toNodeType(to != null ? to.getNodeType() : null)
                    .relationType(r.getRelationType())
                    .confidence(r.getConfidence())
                    .confirmed(r.getConfirmed())
                    .reason(r.getReason())
                    .createdAt(r.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    public void confirmRelation(Long relationId) {
        NodeRelationEntity rel = nodeRelationMapper.selectById(relationId);
        if (rel == null) throw new IllegalArgumentException("关联不存在: " + relationId);

        // 1. PG 标记 confirmed
        nodeRelationMapper.update(null,
                new LambdaUpdateWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getId, relationId)
                        .set(NodeRelationEntity::getConfirmed, true));

        // 2. 同步 Neo4j 边（失败仅记日志，PG 主流程已完成）
        rel.setConfirmed(true);
        boolean synced = graphSyncService.upsertEdge(rel);

        // 3. 标记 syncedNeo4j（供后续补偿任务识别是否需要重试）
        nodeRelationMapper.update(null,
                new LambdaUpdateWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getId, relationId)
                        .set(NodeRelationEntity::getSyncedNeo4j, synced));

        log.info("[Relation] 确认建议关联 id={}, syncedNeo4j={}", relationId, synced);
    }

    @Override
    public void rejectRelation(Long relationId) {
        NodeRelationEntity rel = nodeRelationMapper.selectById(relationId);
        if (rel == null) {
            log.warn("[Relation] 拒绝时关联不存在 id={}", relationId);
            return;
        }

        // 已同步到 Neo4j 的边需要先在 Neo4j 端删除
        if (Boolean.TRUE.equals(rel.getSyncedNeo4j())) {
            graphSyncService.deleteEdge(rel);
        }

        // PG 端物理删除
        nodeRelationMapper.deleteById(relationId);
        log.info("[Relation] 拒绝建议关联 id={}", relationId);
    }
}
