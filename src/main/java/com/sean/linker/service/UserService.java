package com.sean.linker.service;

import com.sean.linker.domain.dto.LoginDTO;
import com.sean.linker.domain.dto.RegisterDTO;
import com.sean.linker.domain.vo.UserVO;

public interface UserService {

    /** 注册新用户，返回 userId */
    Long register(RegisterDTO dto);

    /** 登录，校验通过返回用户信息 */
    UserVO login(LoginDTO dto);
}
