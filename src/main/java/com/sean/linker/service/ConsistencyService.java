package com.sean.linker.service;

import com.sean.linker.domain.vo.ConsistencyReportVO;

import java.util.List;

public interface ConsistencyService {

    /**
     * 跑全项目一致性检查
     * 遍历项目下所有完全确认的下游节点（BLUEPRINT_SEG/TECH_SEG/TEST_CASE），
     * 对每个节点调 ConsistencyAgent 与已确认上游对比，生成报告
     * @return 本次共生成几份报告
     */
    int runProject(Long projectId);

    /** 跑单节点一致性检查（手动重跑或刚 confirm 完时触发） */
    Long runNode(Long nodeId);

    /** 列出项目下最新的报告（每个节点只取最新一份） */
    List<ConsistencyReportVO> listLatest(Long projectId);
}
