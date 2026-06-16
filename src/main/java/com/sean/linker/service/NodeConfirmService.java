package com.sean.linker.service;

import com.sean.linker.domain.vo.PendingNodeVO;

import java.util.List;

public interface NodeConfirmService {

    /** 待确认节点：reqType 未确认 或 module=PENDING */
    List<PendingNodeVO> listPending(Long projectId);

    /** 确认/修正 reqType */
    void confirmReqType(Long nodeId, String reqType);

    /** 确认/修正 module，moduleCode 必须在 project_module 中存在 */
    void confirmModule(Long nodeId, String moduleCode);
}