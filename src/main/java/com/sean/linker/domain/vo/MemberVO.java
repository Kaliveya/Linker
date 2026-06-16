package com.sean.linker.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberVO {

    private Long memberId;
    private Long userId;
    private String name;
    private String email;
    /** BA / PM / DEV / QA */
    private String role;
}
