package com.sean.linker.domain.dto;

import lombok.Data;

@Data
public class AddMemberDTO {

    private Long userId;

    /** BA / PM / DEV / QA */
    private String role;
}
