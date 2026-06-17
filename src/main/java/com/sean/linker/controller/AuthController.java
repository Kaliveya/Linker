package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.domain.dto.LoginDTO;
import com.sean.linker.domain.dto.RegisterDTO;
import com.sean.linker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    /**
     * 注册新用户 — 演示账号自助创建入口
     * Demo 阶段：邮箱 + 密码，密码 SHA-256 入库，不引入 Spring Security
     * 返回 userId，前端登录后存 localStorage 作为后续请求的 userId 上下文
     */
    @PostMapping("/register")
    public CommonResponse register(@RequestBody RegisterDTO dto) {
        return CommonResponse.success(userService.register(dto));
    }

    /**
     * 登录 — 校验邮箱 + 密码
     * 校验通过返回 { userId, name, email }，前端拿 userId 作为后续接口的 userId 参数
     */
    @PostMapping("/login")
    public CommonResponse login(@RequestBody LoginDTO dto) {
        return CommonResponse.success(userService.login(dto));
    }
}
