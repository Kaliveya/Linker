package com.sean.linker.service.impl;

import com.sean.linker.domain.dto.CreateProjectDTO;
import com.sean.linker.domain.entity.ProjectEntity;
import com.sean.linker.mapper.ProjectMapper;
import com.sean.linker.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;

    @Override
    public Long createProject(CreateProjectDTO dto) {
        ProjectEntity entity = ProjectEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .createdBy(dto.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
        projectMapper.insert(entity);
        return entity.getId();
    }
}
