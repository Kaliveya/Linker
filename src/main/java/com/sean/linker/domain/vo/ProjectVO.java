package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVO {

    private Long id;
    private String name;
    private String description;
    private String currentPhase;

    /** 当前用户在此项目中的角色（BA/PM/DEV/QA），无则 null */
    private String myRole;

    private Integer memberCount;
    private LocalDateTime createdAt;
}
