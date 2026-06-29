package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.dto.ConsistencyIssueDTO;
import com.sean.linker.domain.entity.ConsistencyReportEntity;
import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.NotificationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.domain.vo.ConsistencyReportVO;
import com.sean.linker.mapper.ConsistencyReportMapper;
import com.sean.linker.mapper.NodeRelationMapper;
import com.sean.linker.mapper.NotificationMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.ConsistencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsistencyServiceImpl implements ConsistencyService {

    private static final Set<String> DOWNSTREAM_TYPES = Set.of(
            ConstantStatic.BLUEPRINT_SEG,
            ConstantStatic.TECH_SEG,
            ConstantStatic.TEST_CASE);

    private final SemanticNodeMapper semanticNodeMapper;
    private final NodeRelationMapper nodeRelationMapper;
    private final ConsistencyReportMapper consistencyReportMapper;
    private final NotificationMapper notificationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Qualifier("consistencyAgent")
    private final ChatClient consistencyAgent;

    @Override
    public int runProject(Long projectId) {
        List<SemanticNodeEntity> downstreams = semanticNodeMapper.selectList(
                new LambdaQueryWrapper<SemanticNodeEntity>()
                        .eq(SemanticNodeEntity::getProjectId, projectId)
                        .in(SemanticNodeEntity::getNodeType, DOWNSTREAM_TYPES)
                        .eq(SemanticNodeEntity::getModuleStatus, ConstantStatic.MODULE_STATUS_CONFIRMED));

        int count = 0;
        for (SemanticNodeEntity n : downstreams) {
            try {
                runNode(n.getId());
                count++;
            } catch (Exception e) {
                log.error("[Consistency] 节点检查失败 nodeId={}", n.getId(), e);
            }
        }
        log.info("[Consistency] 项目 {} 一致性检查完成，生成 {} 份报告", projectId, count);
        return count;
    }

    @Override
    public Long runNode(Long nodeId) {
        SemanticNodeEntity node = semanticNodeMapper.selectById(nodeId);
        if (node == null) throw new IllegalArgumentException("节点不存在: " + nodeId);

        // 拿全部已确认上游
        List<NodeRelationEntity> upRels = nodeRelationMapper.selectList(
                new LambdaQueryWrapper<NodeRelationEntity>()
                        .eq(NodeRelationEntity::getToNodeId, nodeId)
                        .eq(NodeRelationEntity::getConfirmed, true));

        List<SemanticNodeEntity> upstreams = upRels.isEmpty()
                ? List.of()
                : semanticNodeMapper.selectBatchIds(
                        upRels.stream().map(NodeRelationEntity::getFromNodeId).toList());

        // 调 LLM
        ConsistencyIssueDTO result = consistencyAgent.prompt()
                .user(buildPrompt(node, upstreams))
                .call()
                .entity(ConsistencyIssueDTO.class);

        // 落库
        String issuesJson;
        try {
            issuesJson = objectMapper.writeValueAsString(
                    result.getIssues() == null ? List.of() : result.getIssues());
        } catch (JsonProcessingException e) {
            issuesJson = "[]";
        }
        String highest = computeHighestSeverity(result.getIssues());

        ConsistencyReportEntity report = ConsistencyReportEntity.builder()
                .projectId(node.getProjectId())
                .nodeId(nodeId)
                .aligned(Boolean.TRUE.equals(result.getAligned()))
                .issueCount(result.getIssues() == null ? 0 : result.getIssues().size())
                .highestSeverity(highest)
                .issuesJson(issuesJson)
                .build();
        consistencyReportMapper.insert(report);

        // 有 HIGH 严重级问题 → 生成通知给节点 owner
        if ("HIGH".equals(highest) && node.getOwnerId() != null) {
            NotificationEntity notif = NotificationEntity.builder()
                    .projectId(node.getProjectId())
                    .recipientId(node.getOwnerId())
                    .sourceKind(ConstantStatic.NOTIFICATION_SOURCE_CONSISTENCY)
                    .relatedNodeId(nodeId)
                    .title("一致性问题：" + node.getBizCode())
                    .content("节点 %s 一致性检查发现 %d 个问题（最高严重级 HIGH），请查看一致性报告"
                            .formatted(node.getBizCode(), report.getIssueCount()))
                    .status(ConstantStatic.NOTIFICATION_STATUS_UNREAD)
                    .build();
            notificationMapper.insert(notif);
        }

        log.info("[Consistency] 节点检查完成 nodeId={}, aligned={}, issues={}, highest={}",
                nodeId, report.getAligned(), report.getIssueCount(), highest);
        return report.getId();
    }

    @Override
    public List<ConsistencyReportVO> listLatest(Long projectId) {
        List<ConsistencyReportEntity> all = consistencyReportMapper.selectList(
                new LambdaQueryWrapper<ConsistencyReportEntity>()
                        .eq(ConsistencyReportEntity::getProjectId, projectId)
                        .orderByDesc(ConsistencyReportEntity::getCreatedAt));

        // 每个 nodeId 只保留最新一份
        Map<Long, ConsistencyReportEntity> latestPerNode = new LinkedHashMap<>();
        for (ConsistencyReportEntity r : all) {
            latestPerNode.putIfAbsent(r.getNodeId(), r);
        }

        if (latestPerNode.isEmpty()) return List.of();

        Map<Long, SemanticNodeEntity> nodeMap = semanticNodeMapper.selectBatchIds(latestPerNode.keySet())
                .stream().collect(Collectors.toMap(SemanticNodeEntity::getId, Function.identity()));

        return latestPerNode.values().stream()
                .map(r -> {
                    SemanticNodeEntity n = nodeMap.get(r.getNodeId());
                    return ConsistencyReportVO.builder()
                            .id(r.getId())
                            .nodeId(r.getNodeId())
                            .bizCode(n != null ? n.getBizCode() : null)
                            .nodeType(n != null ? n.getNodeType() : null)
                            .content(n != null ? n.getContent() : null)
                            .aligned(r.getAligned())
                            .issueCount(r.getIssueCount())
                            .highestSeverity(r.getHighestSeverity())
                            .issues(parseIssues(r.getIssuesJson()))
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private String buildPrompt(SemanticNodeEntity node, List<SemanticNodeEntity> upstreams) {
        StringBuilder sb = new StringBuilder();
        sb.append("下游节点（待校验）：\n")
          .append("类型：").append(node.getNodeType()).append("\n")
          .append("编号：").append(node.getBizCode()).append("\n")
          .append("内容：\n").append(node.getContent()).append("\n\n");

        if (upstreams.isEmpty()) {
            sb.append("上游节点：无（请按此情况输出 aligned=false 与 NOT_COVERED 问题）\n");
        } else {
            sb.append("已确认的上游节点（共 ").append(upstreams.size()).append(" 个）：\n");
            for (SemanticNodeEntity u : upstreams) {
                sb.append("---\n")
                  .append("类型：").append(u.getNodeType()).append("\n")
                  .append("编号：").append(u.getBizCode()).append("\n")
                  .append("内容：\n").append(u.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String computeHighestSeverity(List<ConsistencyIssueDTO.Issue> issues) {
        if (issues == null || issues.isEmpty()) return null;
        boolean hasHigh = false, hasMedium = false;
        for (ConsistencyIssueDTO.Issue i : issues) {
            if ("HIGH".equals(i.getSeverity())) hasHigh = true;
            else if ("MEDIUM".equals(i.getSeverity())) hasMedium = true;
        }
        if (hasHigh) return "HIGH";
        if (hasMedium) return "MEDIUM";
        return "LOW";
    }

    private List<ConsistencyIssueDTO.Issue> parseIssues(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, ConsistencyIssueDTO.Issue.class));
        } catch (Exception e) {
            log.warn("[Consistency] 解析 issuesJson 失败", e);
            return List.of();
        }
    }
}
