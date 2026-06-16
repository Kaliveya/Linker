package com.sean.linker.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("graph_sync_outbox")
public class GraphSyncOutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** UPSERT_NODE / DELETE_NODE / UPSERT_EDGE / DELETE_EDGE */
    private String opType;

    /** 操作数据，JSON 格式 */
    private String payload;

    /** PENDING / DONE / FAILED */
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private Integer retryCount = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
