package com.sean.linker.common;

import lombok.Data;

@Data
public class CommonResponse {

    private String code;

    private String message;

    private Object data;

    public static CommonResponse success(Object data) {
        CommonResponse response = new CommonResponse();
        response.setCode(ConstantStatic.SUCCESS_CODE);
        response.setMessage(ConstantStatic.SUCCESS_MSG);
        response.setData(data);
        return response;
    }
}