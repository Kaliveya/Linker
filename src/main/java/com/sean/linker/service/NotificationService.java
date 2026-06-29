package com.sean.linker.service;

import com.sean.linker.domain.vo.NotificationVO;

import java.util.List;

public interface NotificationService {

    /** 查询某用户的通知，可按状态筛选（UNREAD / READ / HANDLED / IGNORED） */
    List<NotificationVO> listNotifications(Long userId, String status);

    /** 更新通知状态（标已读 / 处理 / 忽略），仅允许合法枚举值 */
    void updateStatus(Long notificationId, String status);
}
