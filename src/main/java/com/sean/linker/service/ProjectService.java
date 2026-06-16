package com.sean.linker.service;

import com.sean.linker.domain.dto.CreateProjectDTO;

public interface ProjectService {

    /** 创建项目，返回新项目 id */
    Long createProject(CreateProjectDTO dto);
}
