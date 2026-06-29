package com.sean.linker.service.impl;

import com.sean.linker.common.ConstantStatic;
import com.sean.linker.domain.dto.RelationVerifyResult;
import com.sean.linker.domain.entity.NodeRelationEntity;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import com.sean.linker.mapper.NodeRelationMapper;
import com.sean.linker.mapper.SemanticNodeMapper;
import com.sean.linker.service.RelationCandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationCandidateServiceImpl implements RelationCandidateService {

    private static final int TOP_K = 5;
    private static final float CONFIDENCE_THRESHOLD = 0.6f;

    private final SemanticNodeMapper semanticNodeMapper;
    private final NodeRelationMapper nodeRelationMapper;

    @Qualifier("relationVerifyAgent")
    private final ChatClient relationVerifyAgent;

    @Override
    public void suggestRelations(Long downstreamNodeId) {
        SemanticNodeEntity downstream = semanticNodeMapper.selectById(downstreamNodeId);
        if (downstream == null) {
            log.warn("[Relation] 下游节点不存在 nodeId={}", downstreamNodeId);
            return;
        }
        if (downstream.getModule() == null) {
            // 锚点缺失，跳过；等用户确认 module 后会被 NodeConfirmService 补触发
            log.info("[Relation] 节点 module 未确认，跳过候选关联生成 nodeId={}", downstreamNodeId);
            return;
        }

        String upstreamType = pickUpstreamType(downstream.getNodeType());
        if (upstreamType == null) {
            log.info("[Relation] 节点类型无可关联上游 nodeId={}, nodeType={}",
                    downstreamNodeId, downstream.getNodeType());
            return;
        }
        String upstreamReqType = pickUpstreamReqType(downstream.getNodeType());

        // 第一重 — SQL 锚点过滤 + pgvector 排序
        List<SemanticNodeEntity> candidates = semanticNodeMapper.findCandidates(
                downstream.getProjectId(),
                downstream.getModule(),
                upstreamType,
                upstreamReqType,
                downstream.getId(),
                TOP_K
        );

        log.info("[Relation] 锚点过滤 nodeId={} 命中候选 {} 个 (module={}, upstreamType={}, upstreamReqType={})",
                downstreamNodeId, candidates.size(),
                downstream.getModule(), upstreamType, upstreamReqType);

        for (SemanticNodeEntity upstream : candidates) {
            // 第二重 — LLM 双向校验
            RelationVerifyResult result;
            try {
                result = relationVerifyAgent.prompt()
                        .user("""
                                上游节点（候选）：
                                %s

                                下游节点（待校验）：
                                %s
                                """.formatted(upstream.getContent(), downstream.getContent()))
                        .call()
                        .entity(RelationVerifyResult.class);
            } catch (Exception e) {
                log.error("[Relation] 双向校验失败 downstream={}, upstream={}",
                        downstream.getId(), upstream.getId(), e);
                continue;
            }

            if (result == null || !Boolean.TRUE.equals(result.getMatch())
                    || result.getConfidence() == null
                    || result.getConfidence() < CONFIDENCE_THRESHOLD) {
                log.debug("[Relation] 校验未通过 downstream={}, upstream={}, result={}",
                        downstream.getId(), upstream.getId(), result);
                continue;
            }

            // 第三重 — 落 confirmed=false，等待人工确认
            NodeRelationEntity rel = NodeRelationEntity.builder()
                    .projectId(downstream.getProjectId())
                    .fromNodeId(upstream.getId())
                    .toNodeId(downstream.getId())
                    .relationType(pickRelationType(downstream.getNodeType()))
                    .confidence(result.getConfidence())
                    .confirmed(false)
                    .reason(result.getReason())
                    .syncedNeo4j(false)
                    .build();
            nodeRelationMapper.insert(rel);

            log.info("[Relation] 候选关联入库 from={} to={} confidence={} reason={}",
                    upstream.getId(), downstream.getId(),
                    result.getConfidence(), result.getReason());
        }
    }

    /**
     * 下游 nodeType → 上游 nodeType 的路由
     * BLUEPRINT_SEG → REQUIREMENT（业务方案只关联业务需求）
     * TECH_SEG      → BLUEPRINT_SEG（业务链）；性能/安全链待扩展
     * TEST_CASE     → REQUIREMENT
     */
    private String pickUpstreamType(String downstreamType) {
        return switch (downstreamType) {
            case ConstantStatic.BLUEPRINT_SEG -> ConstantStatic.REQUIREMENT;
            case ConstantStatic.TECH_SEG -> ConstantStatic.BLUEPRINT_SEG;
            case ConstantStatic.TEST_CASE -> ConstantStatic.REQUIREMENT;
            default -> null;
        };
    }

    /**
     * 上游需求的 reqType 锚点
     * BLUEPRINT_SEG 只关联 BUSINESS 需求（设计文档：P1/P2 不参与此层）
     * TECH_SEG → BLUEPRINT_SEG 时 BLUEPRINT_SEG 没有 reqType，传 null
     * TEST_CASE → REQUIREMENT 时业务/性能/安全用例都可能存在，传 null 兜底
     */
    private String pickUpstreamReqType(String downstreamType) {
        if (ConstantStatic.BLUEPRINT_SEG.equals(downstreamType)) {
            return ConstantStatic.REQ_TYPE_BUSINESS;
        }
        return null;
    }

    /**
     * 关联边类型
     * REFINE = 同质细化（需求 → 蓝图段落，业务层细化）
     * DERIVE = 跨层派生（蓝图段落 → 技术段落）
     * IMPLEMENT = 落地实现（需求 → 测试用例）
     */
    private String pickRelationType(String downstreamType) {
        return switch (downstreamType) {
            case ConstantStatic.BLUEPRINT_SEG -> "REFINE";
            case ConstantStatic.TECH_SEG -> "DERIVE";
            case ConstantStatic.TEST_CASE -> "IMPLEMENT";
            default -> "DERIVE";
        };
    }
}
