package com.sean.linker.domain.dto;

import lombok.Data;

@Data
public class AddModuleDTO {

    /** 枚举键，如 LOGIN / PAYMENT */
    private String code;

    /** 显示名，如 登录 / 支付 */
    private String name;
}
