package com.sean.linker.controller;

import com.sean.linker.common.CommonResponse;
import com.sean.linker.domain.dto.ImportDocsDTO;
import com.sean.linker.service.DocsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class DocsController {

    private final DocsService docsService;

    @PostMapping("/{pid}/docs")
    public CommonResponse importDocs(@PathVariable Long pid,
                                     @RequestBody ImportDocsDTO dto) {
        return CommonResponse.success(docsService.importDocs(pid,dto));
    }

}
