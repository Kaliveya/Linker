package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.domain.dto.CreateProjectDTO;
import com.sean.linker.domain.entity.ProjectEntity;
import com.sean.linker.domain.entity.ProjectMemberEntity;
import com.sean.linker.domain.vo.ProjectVO;
import com.sean.linker.mapper.ProjectMapper;
import com.sean.linker.mapper.ProjectMemberMapper;
import com.sean.linker.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    @Transactional
    public Long createProject(CreateProjectDTO dto) {
        ProjectEntity entity = ProjectEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .createdBy(dto.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
        projectMapper.insert(entity);

        // 创建者自动成为该项目 PM 成员，否则"我加入的项目"列表里看不到自己刚建的项目
        if (dto.getUserId() != null) {
            projectMemberMapper.insert(ProjectMemberEntity.builder()
                    .projectId(entity.getId())
                    .userId(dto.getUserId())
                    .role("PM")
                    .build());
        }
        return entity.getId();
    }

    @Override
    public List<ProjectVO> listMyProjects(Long userId) {
        List<ProjectMemberEntity> myMemberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getUserId, userId));
        if (myMemberships.isEmpty()) return Collections.emptyList();

        Set<Long> projectIds = myMemberships.stream()
                .map(ProjectMemberEntity::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, String> myRoleOf = myMemberships.stream()
                .collect(Collectors.toMap(ProjectMemberEntity::getProjectId, ProjectMemberEntity::getRole, (a, b) -> a));

        List<ProjectEntity> projects = projectMapper.selectByIds(projectIds);

        // 一次查全部成员，组内分组得到 memberCount
        List<ProjectMemberEntity> allMembers = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .in(ProjectMemberEntity::getProjectId, projectIds));
        Map<Long, Long> memberCountOf = allMembers.stream()
                .collect(Collectors.groupingBy(ProjectMemberEntity::getProjectId, Collectors.counting()));

        return projects.stream()
                .map(p -> ProjectVO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .currentPhase(p.getCurrentPhase())
                        .myRole(myRoleOf.get(p.getId()))
                        .memberCount(memberCountOf.getOrDefault(p.getId(), 0L).intValue())
                        .createdAt(p.getCreatedAt())
                        .build())
                .sorted(Comparator.comparing(ProjectVO::getCreatedAt).reversed())
                .toList();
    }
}
