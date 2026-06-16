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

    // reqType
    public static final String REQ_TYPE_BUSINESS    = "BUSINESS";
    public static final String REQ_TYPE_PERFORMANCE = "PERFORMANCE";
    public static final String REQ_TYPE_SECURITY    = "SECURITY";

    // project_member.role
    public static final String ROLE_BA  = "BA";
    public static final String ROLE_DEV = "DEV";
    public static final String ROLE_QA  = "QA";
    public static final String ROLE_PM  = "PM";

    // notification
    public static final String NOTIFICATION_SOURCE_ASSIGNMENT  = "ASSIGNMENT";
    public static final String NOTIFICATION_SOURCE_IMPACT      = "IMPACT";
    public static final String NOTIFICATION_SOURCE_CONSISTENCY = "CONSISTENCY";
    public static final String NOTIFICATION_STATUS_UNREAD      = "UNREAD";

    // RabbitMQ
    public static final String PIPELINE_EXCHANGE   = "linker.pipeline";
    public static final String ROUTING_DOC_UPLOAD  = "doc.upload";
    public static final String ROUTING_DOC_VERSION = "doc.version";
    public static final String QUEUE_PIPELINE_UPLOAD  = "q.pipeline.upload";
    public static final String QUEUE_PIPELINE_VERSION = "q.pipeline.version";
}