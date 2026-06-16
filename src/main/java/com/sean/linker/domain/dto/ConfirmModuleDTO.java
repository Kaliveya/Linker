package com.sean.linker.domain.dto;

import lombok.Data;

@Data
public class ConfirmModuleDTO {

    /** 必须在 project_module.code 中存在 */
    private String moduleCode;
}