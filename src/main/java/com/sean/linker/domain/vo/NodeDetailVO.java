package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点详情 — 图谱页点击节点时右侧面板用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDetailVO {

    private Long nodeId;
    private String bizCode;
    private String nodeType;
    private String reqType;
    private Boolean reqTypeConfirmed;
    private String module;
    private String moduleStatus;
    private String status;
    private String content;
    private Long ownerId;
    private LocalDateTime createdAt;

    /** 上游邻居 — 当前节点是该关联的下游 */
    private List<NeighborVO> upstream;
    /** 下游邻居 — 当前节点是该关联的上游 */
    private List<NeighborVO> downstream;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NeighborVO {
        private Long relationId;
        private Long nodeId;
        private String bizCode;
        private String nodeType;
        private String relationType;
        private Float confidence;
    }
}
