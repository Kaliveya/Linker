package com.sean.linker.service;

import com.sean.linker.domain.dto.ImportDocsDTO;

public interface DocsService {

    Long importDocs(Long projectId ,ImportDocsDTO importDocsDTO);

}
