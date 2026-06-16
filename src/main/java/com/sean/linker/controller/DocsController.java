package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.domain.dto.ImportDocsDTO;
import com.sean.linker.service.DocsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class DocsController {

    private final DocsService docsService;

    /**
     * 上传文档 — 场景一/二的起点
     * 同步落盘文档与版本记录后立即返回 docId；ParseAgent 解析、向量化、关联建立等耗时操作
     * 全部异步走 RabbitMQ 流水线，前端可通过 WebSocket（待接入）订阅流水线进度
     * docType:
     *   RESEARCH  — 需求调研文档 → ParseAgent 拆 REQUIREMENT 节点（场景一）
     *   BLUEPRINT — 业务方案 → 拆 BLUEPRINT_SEG 节点（场景二，未实现）
     *   TECH      — 技术方案 → 拆 TECH_SEG 节点（场景二，未实现）
     *   TEST      — 测试用例 → 拆 TEST_CASE 节点（场景二，未实现）
     */
    @PostMapping("/{pid}/docs")
    public CommonResponse importDocs(@PathVariable Long pid,
                                     @RequestBody ImportDocsDTO dto) {
        return CommonResponse.success(docsService.importDocs(pid, dto));
    }
}
