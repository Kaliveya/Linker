package com.sean.linker.service;

import com.sean.linker.domain.dto.CreateProjectDTO;
import com.sean.linker.domain.vo.ProjectVO;

import java.util.List;

public interface ProjectService {

    /** 创建项目，返回新项目 id；同时把创建者自动加为 PM 成员 */
    Long createProject(CreateProjectDTO dto);

    /** 列出当前用户参与的项目（按加入时间倒序），用于顶栏项目下拉 */
    List<ProjectVO> listMyProjects(Long userId);
}
