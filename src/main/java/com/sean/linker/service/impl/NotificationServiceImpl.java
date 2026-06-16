package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.domain.entity.NotificationEntity;
import com.sean.linker.domain.vo.NotificationVO;
import com.sean.linker.mapper.NotificationMapper;
import com.sean.linker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public List<NotificationVO> listNotifications(Long userId, String status) {
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<NotificationEntity>()
                .eq(NotificationEntity::getRecipientId, userId)
                .orderByDesc(NotificationEntity::getCreatedAt);

        if (StringUtils.hasText(status)) {
            wrapper.eq(NotificationEntity::getStatus, status);
        }

        return notificationMapper.selectList(wrapper).stream()
                .map(n -> NotificationVO.builder()
                        .id(n.getId())
                        .projectId(n.getProjectId())
                        .sourceKind(n.getSourceKind())
                        .relatedNodeId(n.getRelatedNodeId())
                        .title(n.getTitle())
                        .content(n.getContent())
                        .status(n.getStatus())
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();
    }
}
