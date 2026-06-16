package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.entity.ProjectModuleEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.PendingNodeVO;
import com.sean.linker.mapper.ProjectModuleMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.GraphSyncService;
import com.sean.linker.service.NodeConfirmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeConfirmServiceImpl implements NodeConfirmService {

    private final SemanticNodeMapper semanticNodeMapper;
    private final ProjectModuleMapper projectModuleMapper;
    private final GraphSyncService graphSyncService;

    private static final Set<String> VALID_REQ_TYPES = Set.of(
            ConstantStatic.REQ_TYPE_BUSINESS,
            ConstantStatic.REQ_TYPE_PERFORMANCE,
            ConstantStatic.REQ_TYPE_SECURITY);

    @Override
    public List<PendingNodeVO> listPending(Long projectId) {
        List<SemanticNodeEntity> nodes = semanticNodeMapper.selectList(
                new LambdaQueryWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getProjectId, projectId)
                        .eq(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT)
                        .and(w -> w
                                .eq(SemanticNodeEntity::getReqTypeConfirmed, false)
                                .or()
                                .eq(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_PENDING)));

        return nodes.stream()
                .map(n -> PendingNodeVO.builder()
                        .nodeId(n.getId())
                        .bizCode(n.getBizCode())
                        .content(n.getContent())
                        .reqType(n.getReqType())
                        .reqTypeConfirmed(n.getReqTypeConfirmed())
                        .module(n.getModule())
                        .moduleStatus(n.getModuleStatus())
                        .build())
                .toList();
    }

    @Override
    public void confirmReqType(Long nodeId, String reqType) {
        if (!VALID_REQ_TYPES.contains(reqType)) {
            throw new IllegalArgumentException("非法 reqType: " + reqType);
        }
        SemanticNodeEntity node = semanticNodeMapper.selectById(nodeId);
        if (node == null) throw new IllegalArgumentException("节点不存在: " + nodeId);

        semanticNodeMapper.update(null,
                new LambdaUpdateWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getId, nodeId)
                        .set(SemanticNodeEntity::getReqType, reqType)
                        .set(SemanticNodeEntity::getReqTypeConfirmed, true));

        syncToNeo4jIfFullyConfirmed(nodeId);
    }

    @Override
    public void confirmModule(Long nodeId, String moduleCode) {
        SemanticNodeEntity node = semanticNodeMapper.selectById(nodeId);
        if (node == null) throw new IllegalArgumentException("节点不存在: " + nodeId);

        Set<String> validCodes = projectModuleMapper.selectList(
                        new LambdaQueryWrapper<ProjectModuleEntity>()
                                .eq(ProjectModuleEntity::getProjectId, node.getProjectId()))
                .stream().map(ProjectModuleEntity::getCode).collect(Collectors.toSet());

        if (!validCodes.contains(moduleCode)) {
            throw new IllegalArgumentException("module 不在项目枚举中: " + moduleCode);
        }

        semanticNodeMapper.update(null,
                new LambdaUpdateWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getId, nodeId)
                        .set(SemanticNodeEntity::getModule, moduleCode)
                        .set(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_CONFIRMED));

        syncToNeo4jIfFullyConfirmed(nodeId);
    }

    /**
     * 两项都确认后才同步到 Neo4j
     * 任一确认动作完成后调用，由该方法判断状态
     */
    private void syncToNeo4jIfFullyConfirmed(Long nodeId) {
        SemanticNodeEntity latest = semanticNodeMapper.selectById(nodeId);
        if (Boolean.TRUE.equals(latest.getReqTypeConfirmed())
                && ConstantStatic.MODULE_STATUS_CONFIRMED.equals(latest.getModuleStatus())) {
            graphSyncService.upsertNode(latest);
        }
    }
}