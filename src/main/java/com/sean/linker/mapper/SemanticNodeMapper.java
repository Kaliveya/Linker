package com.sean.linker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SemanticNodeMapper extends BaseMapper<SemanticNodeEntity> {

    /** 单独更新 embedding，避免全字段 update 覆盖向量列 */
    @Update("UPDATE semantic_node SET embedding = CAST(#{embedding} AS vector) WHERE id = #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);
}