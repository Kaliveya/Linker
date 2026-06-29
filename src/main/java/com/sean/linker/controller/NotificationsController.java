package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationsController {

    private final NotificationService notificationService;

    /**
     * 我的通知列表 — 通知中心数据源
     * 当前用户收到的所有通知，可按状态筛选（UNREAD/READ/HANDLED/IGNORED），
     * 来源 sourceKind 区分：
     *   ASSIGNMENT  — 缺口被指派（场景一）
     *   IMPACT      — 变更传播波及（场景三/五）
     *   CONSISTENCY — 一致性校验问题（场景二）
     * 暂未接入认证，userId 通过 query param 传入
     */
    @GetMapping
    public CommonResponse listNotifications(@RequestParam Long userId,
                                            @RequestParam(required = false) String status) {
        return CommonResponse.success(notificationService.listNotifications(userId, status));
    }

    /**
     * 更新通知状态（标已读 / 处理 / 忽略） — 通知中心操作入口
     * 仅允许 UNREAD / READ / HANDLED / IGNORED 四种值
     */
    @PatchMapping("/{id}/status")
    public CommonResponse updateStatus(@PathVariable Long id,
                                       @RequestBody UpdateStatusBody body) {
        notificationService.updateStatus(id, body.getStatus());
        return CommonResponse.success(null);
    }

    @lombok.Data
    public static class UpdateStatusBody {
        private String status;
    }
}
