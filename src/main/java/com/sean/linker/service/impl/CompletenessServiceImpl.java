package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.dto.AssignmentRequest;
import com.sean.linker.domain.entity.LinkerUserEntity;
import com.sean.linker.domain.entity.NotificationEntity;
import com.sean.linker.domain.entity.ProjectMemberEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.GapItemVO;
import com.sean.linker.mapper.LinkerUserMapper;
import com.sean.linker.mapper.NotificationMapper;
import com.sean.linker.mapper.ProjectMemberMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.CompletenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletenessServiceImpl implements CompletenessService {

    private final SemanticNodeMapper semanticNodeMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final LinkerUserMapper linkerUserMapper;
    private final NotificationMapper notificationMapper;

    /** reqType → 期望下游节点类型 */
    private static final Map<String, List<String>> EXPECTED_DOWNSTREAM = Map.of(
            ConstantStatic.REQ_TYPE_BUSINESS,
            List.of(ConstantStatic.BLUEPRINT_SEG, ConstantStatic.TECH_SEG, ConstantStatic.TEST_CASE),
            ConstantStatic.REQ_TYPE_PERFORMANCE,
            List.of(ConstantStatic.TECH_SEG, ConstantStatic.TEST_CASE),
            ConstantStatic.REQ_TYPE_SECURITY,
            List.of(ConstantStatic.TECH_SEG, ConstantStatic.TEST_CASE)
    );

    /** 缺口节点类型 → 应该指派的角色 */
    private static final Map<String, String> TYPE_TO_ROLE = Map.of(
            ConstantStatic.BLUEPRINT_SEG, ConstantStatic.ROLE_BA,
            ConstantStatic.TECH_SEG,      ConstantStatic.ROLE_DEV,
            ConstantStatic.TEST_CASE,     ConstantStatic.ROLE_QA
    );

    @Override
    public List<GapItemVO> computeGaps(Long projectId) {
        // 查所有已确认 reqType 的需求节点（未确认的不算缺口，等用户确认后再算）
        List<SemanticNodeEntity> requirements = semanticNodeMapper.selectList(
                new LambdaQueryWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getProjectId, projectId)
                        .eq(SemanticNodeEntity::getNodeType, ConstantStatic.REQUIREMENT)
                        .eq(SemanticNodeEntity::getReqTypeConfirmed, true));

        if (requirements.isEmpty()) return Collections.emptyList();

        // 预加载项目成员 + 用户表，避免循环查库
        Map<String, List<GapItemVO.Candidate>> roleToCandidates = loadCandidatesByRole(projectId);

        List<GapItemVO> result = new ArrayList<>();
        for (SemanticNodeEntity req : requirements) {
            List<String> expectedTypes = EXPECTED_DOWNSTREAM.get(req.getReqType());
            if (expectedTypes == null) continue;

            List<GapItemVO.MissingItem> gaps = new ArrayList<>();
            for (String missingType : expectedTypes) {
                String role = TYPE_TO_ROLE.get(missingType);
                gaps.add(GapItemVO.MissingItem.builder()
                        .missingType(missingType)
                        .missingTypeName(typeName(missingType))
                        .suggestedRole(role)
                        .candidates(roleToCandidates.getOrDefault(role, Collections.emptyList()))
                        .build());
            }

            result.add(GapItemVO.builder()
                    .nodeId(req.getId())
                    .bizCode(req.getBizCode())
                    .content(req.getContent())
                    .reqType(req.getReqType())
                    .gaps(gaps)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void assignGaps(Long projectId, List<AssignmentRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        // 批量查节点
        Set<Long> nodeIds = requests.stream().map(AssignmentRequest::getNodeId).collect(Collectors.toSet());
        Map<Long, SemanticNodeEntity> nodeMap = semanticNodeMapper.selectByIds(nodeIds)
                .stream().collect(Collectors.toMap(SemanticNodeEntity::getId, n -> n));

        for (AssignmentRequest req : requests) {
            SemanticNodeEntity node = nodeMap.get(req.getNodeId());
            if (node == null) {
                log.warn("[Assignment] 节点不存在 nodeId={}", req.getNodeId());
                continue;
            }

            String title = "%s 缺少%s，请补充".formatted(node.getBizCode(), typeName(req.getTaskType()));
            String content = "节点 %s（reqType=%s）需要补充 %s。".formatted(
                    node.getBizCode(), node.getReqType(), typeName(req.getTaskType()));

            NotificationEntity notification = NotificationEntity.builder()
                    .projectId(projectId)
                    .recipientId(req.getAssigneeId())
                    .sourceKind(ConstantStatic.NOTIFICATION_SOURCE_ASSIGNMENT)
                    .relatedNodeId(node.getId())
                    .title(title)
                    .content(content)
                    .status(ConstantStatic.NOTIFICATION_STATUS_UNREAD)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationMapper.insert(notification);
        }
    }

    /** 按角色聚合候选成员，一次性预加载 */
    private Map<String, List<GapItemVO.Candidate>> loadCandidatesByRole(Long projectId) {
        List<ProjectMemberEntity> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getProjectId, projectId));

        if (members.isEmpty()) return Collections.emptyMap();

        Set<Long> userIds = members.stream().map(ProjectMemberEntity::getUserId).collect(Collectors.toSet());
        Map<Long, String> userNameMap = linkerUserMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(LinkerUserEntity::getId, LinkerUserEntity::getName));

        Map<String, List<GapItemVO.Candidate>> grouped = new HashMap<>();
        for (ProjectMemberEntity m : members) {
            grouped.computeIfAbsent(m.getRole(), k -> new ArrayList<>())
                    .add(GapItemVO.Candidate.builder()
                            .userId(m.getUserId())
                            .name(userNameMap.getOrDefault(m.getUserId(), "未知"))
                            .build());
        }
        return grouped;
    }

    private String typeName(String nodeType) {
        return switch (nodeType) {
            case ConstantStatic.BLUEPRINT_SEG -> "业务方案";
            case ConstantStatic.TECH_SEG      -> "技术方案";
            case ConstantStatic.TEST_CASE     -> "测试用例";
            default -> nodeType;
        };
    }
}