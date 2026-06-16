package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.domain.dto.ConfirmModuleDTO;
import com.sean.linker.domain.dto.ConfirmReqTypeDTO;
import com.sean.linker.service.NodeConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nodes")
public class NodesController {

    private final NodeConfirmService nodeConfirmService;

    /**
     * 确认或修正节点的 reqType — 解析后人工把关
     * 把 LLM 初判的 reqType 转为人工确认状态（req_type_confirmed=true），
     * 节点确认后才参与变更传播路由与缺口计算，是场景一通往场景三/五的必经步骤
     */
    @PatchMapping("/{nodeId}/req-type")
    public CommonResponse confirmReqType(@PathVariable Long nodeId,
                                         @RequestBody ConfirmReqTypeDTO dto) {
        nodeConfirmService.confirmReqType(nodeId, dto.getReqType());
        return CommonResponse.success(null);
    }

    /**
     * 指定节点的 module — 解析后人工把关
     * 处理 LLM 未能识别 module（module_status=PENDING）的节点，前端从项目模块列表中选一项提交。
     * 后端校验 moduleCode 必须在 project_module 中存在，确认后节点才进入场景二的关联建立流程
     */
    @PatchMapping("/{nodeId}/module")
    public CommonResponse confirmModule(@PathVariable Long nodeId,
                                        @RequestBody ConfirmModuleDTO dto) {
        nodeConfirmService.confirmModule(nodeId, dto.getModuleCode());
        return CommonResponse.success(null);
    }
}
