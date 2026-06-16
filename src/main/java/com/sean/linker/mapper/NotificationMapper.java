package com.sean.linker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sean.linker.domain.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}
