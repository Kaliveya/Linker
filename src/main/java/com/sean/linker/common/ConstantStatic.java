package com.sean.linker.common;

public class ConstantStatic {

    public static final String SUCCESS_CODE = "0000";

    public static final String SUCCESS_MSG = "操作成功";

    public static final String ERROR_CODE = "1000";

    public static final Integer OUTPUT_VALIDATION_ADVISOR_ORDER = 100;

    public static final Integer MODULE_VALIDATION_ADVISOR_ORDER = 100;

    public static final String RESEARCH = "RESEARCH";

    public static final String BLUEPRINT = "BLUEPRINT";

    public static final String TECH = "TECH";

    public static final String TEST = "TEST";

    public static final String MODULE_STATUS_PENDING = "PENDING";

    public static final String MODULE_STATUS_CONFIRMED = "CONFIRMED";

    // semantic_node.node_type
    public static final String REQUIREMENT   = "REQUIREMENT";
    public static final String BLUEPRINT_SEG = "BLUEPRINT_SEG";
    public static final String TECH_SEG      = "TECH_SEG";
    public static final String TEST_CASE     = "TEST_CASE";

    // RabbitMQ
    public static final String PIPELINE_EXCHANGE   = "linker.pipeline";
    public static final String ROUTING_DOC_UPLOAD  = "doc.upload";
    public static final String ROUTING_DOC_VERSION = "doc.version";
    public static final String QUEUE_PIPELINE_UPLOAD  = "q.pipeline.upload";
    public static final String QUEUE_PIPELINE_VERSION = "q.pipeline.version";
}