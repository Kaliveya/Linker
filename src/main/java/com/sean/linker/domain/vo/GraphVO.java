package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 项目语义图谱 — 前端可视化数据源
 * 直接产出 vis-network / cytoscape 所需 {nodes, edges} 结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphVO {
    private List<GraphNodeVO> nodes;
    private List<GraphEdgeVO> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNodeVO {
        private Long nodeId;
        private String bizCode;
        /** REQUIREMENT / BLUEPRINT_SEG / TECH_SEG / TEST_CASE */
        private String nodeType;
        private String module;
        /** 只有 REQUIREMENT 有 */
        private String reqType;
        /** 截断后的预览文本，避免传整段全文 */
        private String contentPreview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdgeVO {
        private Long id;
        private Long fromNodeId;
        private Long toNodeId;
        /** REFINE / DERIVE / IMPLEMENT */
        private String relationType;
        private Float confidence;
    }
}
