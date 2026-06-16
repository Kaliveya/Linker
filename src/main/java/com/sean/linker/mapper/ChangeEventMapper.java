package com.sean.linker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sean.linker.domain.entity.ChangeEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeEventMapper extends BaseMapper<ChangeEventEntity> {
}
