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
import com.sean.linker.service.RelationCandidateService;
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
    private final RelationCandidateService relationCandidateService;

    private static final Set<String> VALID_REQ_TYPES = Set.of(
            ConstantStatic.REQ_TYPE_BUSINESS,
            ConstantStatic.REQ_TYPE_PERFORMANCE,
            ConstantStatic.REQ_TYPE_SECURITY);

    /** 场景二下游节点类型：confirm module 后需要触发候选关联生成 */
    private static final Set<String> DOWNSTREAM_NODE_TYPES = Set.of(
            ConstantStatic.BLUEPRINT_SEG,
            ConstantStatic.TECH_SEG,
            ConstantStatic.TEST_CASE);

    @Override
    public List<PendingNodeVO> listPending(Long projectId) {
        // REQUIREMENT: reqType 或 module 任一未确认即待确认
        // 下游类型(BLUEPRINT_SEG/TECH_SEG/TEST_CASE): 只看 module
        List<SemanticNodeEntity> nodes = semanticNodeMapper.selectList(
                new LambdaQueryWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getProjectId, projectId)
                        .and(outer -> outer
                                // 需求节点：reqType 未确认 或 module=PENDING
                                .and(w -> w
                                        .eq(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT)
                                        .and(inner -> inner
                                                .eq(SemanticNodeEntity::getReqTypeConfirmed, false)
                                                .or()
                                                .eq(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_PENDING)))
                                // 下游节点：只看 module=PENDING
                                .or(w -> w
                                        .in(SemanticNodeEntity::getNodeType, DOWNSTREAM_NODE_TYPES)
                                        .eq(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_PENDING))));

        return nodes.stream()
                .map(n -> PendingNodeVO.builder()
                        .nodeId(n.getId())
                        .bizCode(n.getBizCode())
                        .content(n.getContent())
                        .nodeType(n.getNodeType())
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

        // 场景二：下游节点 module 确认后补触发候选关联生成
        // (流水线中 PENDING 节点被跳过，必须在此补做)
        if (DOWNSTREAM_NODE_TYPES.contains(node.getNodeType())) {
            try {
                relationCandidateService.suggestRelations(nodeId);
            } catch (Exception e) {
                log.error("[NodeConfirm] 候选关联生成失败 nodeId={}", nodeId, e);
            }
        }
    }

    /**
     * 完全确认后同步到 Neo4j，按 nodeType 判断"完全确认"的条件
     *   REQUIREMENT  — reqType 已确认 && module 已确认
     *   其他下游类型  — 只要 module 已确认
     */
    private void syncToNeo4jIfFullyConfirmed(Long nodeId) {
        SemanticNodeEntity latest = semanticNodeMapper.selectById(nodeId);
        if (!ConstantStatic.MODULE_STATUS_CONFIRMED.equals(latest.getModuleStatus())) {
            return;
        }
        if (ConstantStatic.REQUIREMENT.equals(latest.getNodeType())
                && !Boolean.TRUE.equals(latest.getReqTypeConfirmed())) {
            return;
        }
        graphSyncService.upsertNode(latest);
    }
}