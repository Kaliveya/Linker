package com.sean.linker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sean.linker.domain.dto.AddMemberDTO;
import com.sean.linker.domain.entity.LinkerUserEntity;
import com.sean.linker.domain.entity.ProjectMemberEntity;
import com.sean.linker.domain.vo.MemberVO;
import com.sean.linker.mapper.LinkerUserMapper;
import com.sean.linker.mapper.ProjectMemberMapper;
import com.sean.linker.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final ProjectMemberMapper projectMemberMapper;
    private final LinkerUserMapper linkerUserMapper;

    @Override
    public Long addMember(Long projectId, AddMemberDTO dto) {
        // 校验用户存在
        LinkerUserEntity user = linkerUserMapper.selectById(dto.getUserId());
        if (user == null) throw new IllegalArgumentException("用户不存在: " + dto.getUserId());

        ProjectMemberEntity entity = ProjectMemberEntity.builder()
                .projectId(projectId)
                .userId(dto.getUserId())
                .role(dto.getRole())
                .build();
        projectMemberMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public List<MemberVO> listMembers(Long projectId) {
        List<ProjectMemberEntity> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getProjectId, projectId));

        if (members.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = members.stream().map(ProjectMemberEntity::getUserId).collect(Collectors.toSet());
        Map<Long, LinkerUserEntity> userMap = linkerUserMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(LinkerUserEntity::getId, u -> u));

        List<MemberVO> result = new ArrayList<>();
        for (ProjectMemberEntity m : members) {
            LinkerUserEntity user = userMap.get(m.getUserId());
            result.add(MemberVO.builder()
                    .memberId(m.getId())
                    .userId(m.getUserId())
                    .name(user != null ? user.getName() : "未知")
                    .email(user != null ? user.getEmail() : null)
                    .role(m.getRole())
                    .build());
        }
        return result;
    }
}
