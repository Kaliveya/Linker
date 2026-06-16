package com.sean.linker.service;

import com.sean.linker.domain.dto.AssignmentRequest;
import com.sean.linker.domain.vo.GapItemVO;

import java.util.List;

public interface CompletenessService {

    /** 实时计算项目当前缺口清单 */
    List<GapItemVO> computeGaps(Long projectId);

    /** 按 PM 提交的指派列表生成 ASSIGNMENT 通知 */
    void assignGaps(Long projectId, List<AssignmentRequest> requests);
}