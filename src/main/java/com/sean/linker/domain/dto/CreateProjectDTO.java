package com.sean.linker.domain.dto;

import com.sean.linker.common.BaseRequest;
import lombok.Data;

@Data
public class CreateProjectDTO extends BaseRequest {

    private String name;
    private String description;
}
