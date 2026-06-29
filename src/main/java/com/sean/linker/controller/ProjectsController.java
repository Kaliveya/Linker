package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.domain.dto.*;
import com.sean.linker.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectsController {

    private final ProjectService projectService;
    private final MemberService memberService;
    private final ModuleService moduleService;
    private final CompletenessService completenessService;
    private final NodeConfirmService nodeConfirmService;
    private final GraphService graphService;
    private final ConsistencyService consistencyService;

    // ───────────────────────────── 项目本身 ─────────────────────────────

    /**
     * 新建项目 — 场景一起点
     * PM 在系统中新建一个交付项目（如"客户A实施项目"），返回 projectId 用于后续配置成员、模块、上传文档
     */
    @PostMapping
    public CommonResponse createProject(@RequestBody CreateProjectDTO dto) {
        return CommonResponse.success(projectService.createProject(dto));
    }

    /**
     * 当前用户参与的项目列表 — 顶栏「项目下拉」数据源
     * 暂未接入认证，userId 通过 query param 传入
     */
    @GetMapping
    public CommonResponse listMyProjects(@RequestParam Long userId) {
        return CommonResponse.success(projectService.listMyProjects(userId));
    }

    // ───────────────────────────── 成员管理 ─────────────────────────────

    /**
     * 添加项目成员（指定角色）
     * 给项目分配 BA/PM/DEV/QA，后续完整性检查按角色匹配候选指派人，缺口指派也只能从这些成员中选
     */
    @PostMapping("/{pid}/members")
    public CommonResponse addMember(@PathVariable Long pid,
                                    @RequestBody AddMemberDTO dto) {
        return CommonResponse.success(memberService.addMember(pid, dto));
    }

    /**
     * 项目成员列表
     * 前端"成员面板"与缺口面板"指派下拉框"的数据源
     */
    @GetMapping("/{pid}/members")
    public CommonResponse listMembers(@PathVariable Long pid) {
        return CommonResponse.success(memberService.listMembers(pid));
    }

    // ───────────────────────────── 功能模块管理 ─────────────────────────────

    /**
     * 新增项目功能模块（闭域词表）
     * 关联准确性的第一重锚点。PM 上传需求文档前必须先配置模块（登录/支付/权限...），
     * ParseAgent 解析时会拉取该列表注入 prompt，强制 LLM 只输出枚举内 module，
     * 否则节点会进入 PENDING 状态等人工指派
     */
    @PostMapping("/{pid}/modules")
    public CommonResponse addModule(@PathVariable Long pid,
                                    @RequestBody AddModuleDTO dto) {
        return CommonResponse.success(moduleService.addModule(pid, dto));
    }

    /**
     * 项目功能模块列表
     * 前端在"指定 module"操作时作为下拉框数据源；ParseAgent 调用前也通过此接口拉取闭域词表
     */
    @GetMapping("/{pid}/modules")
    public CommonResponse listModules(@PathVariable Long pid) {
        return CommonResponse.success(moduleService.listModules(pid));
    }

    // ───────────────────────────── 完整性检查 / 指派 ─────────────────────────────

    /**
     * 实时计算项目缺口清单 — 场景一·状态A
     * 列出每个已确认 reqType 的需求节点缺哪些下游产出物（业务方案/技术方案/测试用例），
     * 同时按角色映射给出候选指派人。每次调用实时计算，节点新增或下游补齐后立即反映
     */
    @GetMapping("/{pid}/gaps")
    public CommonResponse listGaps(@PathVariable Long pid) {
        return CommonResponse.success(completenessService.computeGaps(pid));
    }

    /**
     * 一键指派缺口 — 场景一·状态A
     * PM 在缺口面板勾选每个缺口的具体 assignee 后提交，系统按 (nodeId, assigneeId, taskType) 三元组
     * 批量生成 ASSIGNMENT 通知写入 notification 表，被指派人在通知中心可见
     */
    @PostMapping("/{pid}/assignments")
    public CommonResponse assignGaps(@PathVariable Long pid,
                                     @RequestBody List<AssignmentRequest> requests) {
        completenessService.assignGaps(pid, requests);
        return CommonResponse.success(null);
    }

    // ───────────────────────────── 待确认节点 ─────────────────────────────

    /**
     * 待确认节点清单 — 解析后人工确认环节
     * 返回 ParseAgent 解析后所有需要人工把关的需求节点：
     * - reqType 仍是 LLM 初判（req_type_confirmed=false）→ 等用户确认才参与路由
     * - module 识别失败（module_status=PENDING）→ 等用户从模块下拉中指定
     * 前端"待确认面板"由此接口驱动
     */
    @GetMapping("/{pid}/nodes/pending")
    public CommonResponse listPending(@PathVariable Long pid) {
        return CommonResponse.success(nodeConfirmService.listPending(pid));
    }

    // ───────────────────────────── 场景二 · 图谱 ─────────────────────────────

    /**
     * 项目语义图谱 — 场景二可视化
     * 返回所有已完全确认的节点 + 已确认的关联边，供前端 vis-network 渲染
     */
    @GetMapping("/{pid}/graph")
    public CommonResponse getGraph(@PathVariable Long pid) {
        return CommonResponse.success(graphService.getProjectGraph(pid));
    }

    // ───────────────────────────── 场景二 · 一致性校验 ─────────────────────────────

    /**
     * 跑项目级一致性检查 — 场景二·ConsistencyAgent
     * 遍历项目下所有完全确认的下游节点（业务方案段/技术方案段/测试用例），
     * 每个节点调 LLM 与其全部已确认上游对比，输出问题列表落表 + 生成 CONSISTENCY 通知
     */
    @PostMapping("/{pid}/consistency/run")
    public CommonResponse runConsistency(@PathVariable Long pid) {
        int count = consistencyService.runProject(pid);
        return CommonResponse.success(count);
    }

    /**
     * 列出项目下最新一致性报告（每个节点只取最新一份）
     */
    @GetMapping("/{pid}/consistency/reports")
    public CommonResponse listConsistencyReports(@PathVariable Long pid) {
        return CommonResponse.success(consistencyService.listLatest(pid));
    }
}
