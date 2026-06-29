package com.sean.linker.service;

import com.sean.linker.domain.dto.LoginDTO;
import com.sean.linker.domain.dto.RegisterDTO;
import com.sean.linker.domain.vo.UserVO;

import java.util.List;

public interface UserService {

    /** 注册新用户，返回 userId */
    Long register(RegisterDTO dto);

    /** 登录，校验通过返回用户信息 */
    UserVO login(LoginDTO dto);

    /** 列出所有已注册用户 —— 用于「添加成员」下拉，前端在外部按已是成员筛掉 */
    List<UserVO> listAll();
}
