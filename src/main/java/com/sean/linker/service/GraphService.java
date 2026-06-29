package com.sean.linker.service;

import com.sean.linker.domain.vo.GraphVO;
import com.sean.linker.domain.vo.NodeDetailVO;

public interface GraphService {

    /** 项目语义图谱 — 已确认节点 + 已确认关联边 */
    GraphVO getProjectGraph(Long projectId);

    /** 节点详情 + 上下游邻居 */
    NodeDetailVO getNodeDetail(Long nodeId);
}
