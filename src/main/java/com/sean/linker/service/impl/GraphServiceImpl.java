package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.GraphVO;
import com.sean.linker.domain.vo.NodeDetailVO;
import com.sean.linker.mapper.NodeRelationMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private static final int CONTENT_PREVIEW_LENGTH = 60;

    private final SemanticNodeMapper semanticNodeMapper;
    private final NodeRelationMapper nodeRelationMapper;

    @Override
    public GraphVO getProjectGraph(Long projectId) {
        // 1. 拿项目下全部已确认关联边
        List<NodeRelationEntity> rels = nodeRelationMapper.selectList(
                new LambdaQueryWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getProjectId, projectId)
                        .eq(NodeRelationEntity::getConfirmed, true));

        // 2. 拿项目下全部"完全确认"的节点（哪怕没有边也展示）
        //    REQUIREMENT: reqTypeConfirmed=true AND moduleStatus=CONFIRMED
        //    下游类型:    moduleStatus=CONFIRMED
        List<SemanticNodeEntity> nodes = semanticNodeMapper.selectList(
                new LambdaQueryWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getProjectId, projectId)
                        .eq(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_CONFIRMED)
                        .and(w -> w
                                .ne(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT)
                                .or(inner -> inner
                                        .eq(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT)
                                        .eq(SemanticNodeEntity::getReqTypeConfirmed, true))));

        List<GraphVO.GraphNodeVO> nodeVOs = nodes.stream()
                .map(n -> GraphVO.GraphNodeVO.builder()
                        .nodeId(n.getId())
                        .bizCode(n.getBizCode())
                        .nodeType(n.getNodeType())
                        .module(n.getModule())
                        .reqType(n.getReqType())
                        .contentPreview(preview(n.getContent()))
                        .build())
                .toList();

        List<GraphVO.GraphEdgeVO> edgeVOs = rels.stream()
                .map(r -> GraphVO.GraphEdgeVO.builder()
                        .id(r.getId())
                        .fromNodeId(r.getFromNodeId())
                        .toNodeId(r.getToNodeId())
                        .relationType(r.getRelationType())
                        .confidence(r.getConfidence())
                        .build())
                .toList();

        return GraphVO.builder().nodes(nodeVOs).edges(edgeVOs).build();
    }

    @Override
    public NodeDetailVO getNodeDetail(Long nodeId) {
        SemanticNodeEntity n = semanticNodeMapper.selectById(nodeId);
        if (n == null) throw new IllegalArgumentException("节点不存在: " + nodeId);

        // 上游：当前节点是下游 (to_node_id = nodeId)
        List<NodeRelationEntity> upRels = nodeRelationMapper.selectList(
                new LambdaQueryWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getToNodeId, nodeId)
                        .eq(NodeRelationEntity::getConfirmed, true));

        // 下游：当前节点是上游 (from_node_id = nodeId)
        List<NodeRelationEntity> downRels = nodeRelationMapper.selectList(
                new LambdaQueryWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getFromNodeId, nodeId)
                        .eq(NodeRelationEntity::getConfirmed, true));

        // 一次拉对端节点 bizCode/nodeType
        Set<Long> otherIds = new HashSet<>();
        upRels.forEach(r -> otherIds.add(r.getFromNodeId()));
        downRels.forEach(r -> otherIds.add(r.getToNodeId()));

        Map<Long, SemanticNodeEntity> otherMap = otherIds.isEmpty()
                ? Map.of()
                : semanticNodeMapper.selectBatchIds(otherIds).stream()
                        .collect(Collectors.toMap(SemanticNodeEntity::getId, Function.identity()));

        return NodeDetailVO.builder()
                .nodeId(n.getId())
                .bizCode(n.getBizCode())
                .nodeType(n.getNodeType())
                .reqType(n.getReqType())
                .reqTypeConfirmed(n.getReqTypeConfirmed())
                .module(n.getModule())
                .moduleStatus(n.getModuleStatus())
                .status(n.getStatus())
                .content(n.getContent())
                .ownerId(n.getOwnerId())
                .createdAt(n.getCreatedAt())
                .upstream(upRels.stream()
                        .map(r -> toNeighbor(r, r.getFromNodeId(), otherMap))
                        .toList())
                .downstream(downRels.stream()
                        .map(r -> toNeighbor(r, r.getToNodeId(), otherMap))
                        .toList())
                .build();
    }

    private NodeDetailVO.NeighborVO toNeighbor(NodeRelationEntity r,
                                               Long otherId,
                                               Map<Long, SemanticNodeEntity> otherMap) {
        SemanticNodeEntity other = otherMap.get(otherId);
        return NodeDetailVO.NeighborVO.builder()
                .relationId(r.getId())
                .nodeId(otherId)
                .bizCode(other != null ? other.getBizCode() : null)
                .nodeType(other != null ? other.getNodeType() : null)
                .relationType(r.getRelationType())
                .confidence(r.getConfidence())
                .build();
    }

    private String preview(String content) {
        if (content == null) return "";
        return content.length() <= CONTENT_PREVIEW_LENGTH
                ? content
                : content.substring(0, CONTENT_PREVIEW_LENGTH) + "...";
    }
}
