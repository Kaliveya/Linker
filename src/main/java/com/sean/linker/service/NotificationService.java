package com.sean.linker.service;

import com.sean.linker.domain.vo.NotificationVO;

import java.util.List;

public interface NotificationService {

    /** 查询某用户的通知，可按状态筛选（UNREAD / READ / HANDLED / IGNORED） */
    List<NotificationVO> listNotifications(Long userId, String status);
}
