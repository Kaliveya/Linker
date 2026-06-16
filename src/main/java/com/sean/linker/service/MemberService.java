package com.sean.linker.service;

import com.sean.linker.domain.dto.AddMemberDTO;
import com.sean.linker.domain.vo.MemberVO;

import java.util.List;

public interface MemberService {

    /** 添加项目成员（指派角色），返回 member id */
    Long addMember(Long projectId, AddMemberDTO dto);

    /** 项目成员列表 */
    List<MemberVO> listMembers(Long projectId);
}
