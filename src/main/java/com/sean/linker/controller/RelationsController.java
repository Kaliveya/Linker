package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RelationsController {

    private final RelationService relationService;

    /**
     * 列出项目下的建议关联 — 场景二·关联确认页数据源
     * confirmed=false 看待确认，confirmed=true 看已确认，不传看全部
     */
    @GetMapping("/projects/{pid}/relations")
    public CommonResponse listRelations(@PathVariable Long pid,
                                        @RequestParam(required = false) Boolean confirmed) {
        return CommonResponse.success(relationService.listRelations(pid, confirmed));
    }

    /**
     * 确认建议关联 — 当前阶段只更新 DB
     * Phase 3 完整实现时此接口会触发 Neo4j 边同步
     */
    @PostMapping("/relations/{relId}/confirm")
    public CommonResponse confirmRelation(@PathVariable Long relId) {
        relationService.confirmRelation(relId);
        return CommonResponse.success(null);
    }

    /**
     * 拒绝/删除建议关联
     */
    @DeleteMapping("/relations/{relId}")
    public CommonResponse rejectRelation(@PathVariable Long relId) {
        relationService.rejectRelation(relId);
        return CommonResponse.success(null);
    }
}
