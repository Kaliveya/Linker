package com.sean.linker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sean.linker.domain.entity.SemanticNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SemanticNodeMapper extends BaseMapper<SemanticNodeEntity> {

    /** 单独更新 embedding，避免全字段 update 覆盖向量列 */
    @Update("UPDATE semantic_node SET embedding = CAST(#{embedding} AS vector) WHERE id = #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    /**
     * 场景二关联候选检索（系统命门 · 第一重防御 = 结构化锚点过滤）
     *
     * 在给定 module + upstreamType（可选 + upstreamReqType）的窄范围里，
     * 用 pgvector 余弦距离按相似度排序，返回 topK 候选上游。
     *
     * upstreamReqType 传 null 时不加 req_type 过滤（如 TECH_SEG → BLUEPRINT_SEG 的场景）。
     * 必须排除下游节点自身。
     */
    @Select("""
            <script>
            SELECT * FROM semantic_node
            WHERE project_id = #{projectId}
              AND module = #{module}
              AND node_type = #{upstreamType}
              AND id != #{downstreamId}
              <if test='upstreamReqType != null'> AND req_type = #{upstreamReqType} </if>
            ORDER BY embedding <![CDATA[<=>]]> (
                SELECT embedding FROM semantic_node WHERE id = #{downstreamId}
            )
            LIMIT #{topK}
            </script>
            """)
    List<SemanticNodeEntity> findCandidates(
            @Param("projectId") Long projectId,
            @Param("module") String module,
            @Param("upstreamType") String upstreamType,
            @Param("upstreamReqType") String upstreamReqType,
            @Param("downstreamId") Long downstreamId,
            @Param("topK") int topK
    );
}