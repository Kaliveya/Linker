package com.sean.linker.service;

import com.sean.linker.domain.dto.AddModuleDTO;
import com.sean.linker.domain.vo.ModuleVO;

import java.util.List;

public interface ModuleService {

    /** 配置项目功能模块（如登录/支付），返回模块 id */
    Long addModule(Long projectId, AddModuleDTO dto);

    /** 项目模块列表，供 ParseAgent 拉取闭域词表与前端下拉框使用 */
    List<ModuleVO> listModules(Long projectId);
}
