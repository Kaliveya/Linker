package com.sean.linker.domain.dto;

import com.sean.linker.common.BaseRequest;
import lombok.Data;

@Data
public class ImportDocsDTO extends BaseRequest {

    /**
     * 文件地址_取本地地址模拟上传,仅支持md格式
     */
    private String fileUrl;

    /**
     * 文档类型
     * 1.RESEARCH
     * 2.BLUEPRINT
     * 3.TECH
     * 4.TEST
     */
    private String docType;

}
