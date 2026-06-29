package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UsersController {

    private final UserService userService;

    /**
     * 列出所有已注册用户 —— 前端「添加成员」下拉数据源
     * Demo 阶段不分页、不带搜索；前端拿到完整列表后剔除"已是项目成员的用户"
     */
    @GetMapping
    public CommonResponse listAll() {
        return CommonResponse.success(userService.listAll());
    }
}
