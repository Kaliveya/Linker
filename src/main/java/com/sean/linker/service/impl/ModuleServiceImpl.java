package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.domain.dto.AddModuleDTO;
import com.sean.linker.domain.entity.ProjectModuleEntity;
import com.sean.linker.domain.vo.ModuleVO;
import com.sean.linker.mapper.ProjectModuleMapper;
import com.sean.linker.service.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleServiceImpl implements ModuleService {

    private final ProjectModuleMapper projectModuleMapper;

    @Override
    public Long addModule(Long projectId, AddModuleDTO dto) {
        ProjectModuleEntity entity = ProjectModuleEntity.builder()
                .projectId(projectId)
                .code(dto.getCode())
                .name(dto.getName())
                .build();
        projectModuleMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public List<ModuleVO> listModules(Long projectId) {
        return projectModuleMapper.selectList(
                        new LambdaQueryWrapper<ProjectModuleEntity>()
                                .eq(ProjectModuleEntity::getProjectId, projectId))
                .stream()
                .map(m -> ModuleVO.builder()
                        .id(m.getId())
                        .code(m.getCode())
                        .name(m.getName())
                        .build())
                .toList();
    }
}
