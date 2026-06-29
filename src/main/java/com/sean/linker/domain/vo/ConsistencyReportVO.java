package com.sean.linker.domain.vo;

import com.sean.linker.domain.dto.ConsistencyIssueDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistencyReportVO {

    private Long id;
    private Long nodeId;
    private String bizCode;
    private String nodeType;
    private String content;

    private Boolean aligned;
    private Integer issueCount;
    private String highestSeverity;

    /** 反序列化后的 issue 列表，前端直接渲染 */
    private List<ConsistencyIssueDTO.Issue> issues;

    private LocalDateTime createdAt;
}
