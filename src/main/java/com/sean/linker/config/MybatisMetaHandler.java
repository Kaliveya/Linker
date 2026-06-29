package com.sean.linker.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 字段自动填充 —— 让实体上的 @TableField(fill = FieldFill.INSERT/UPDATE) 真正生效。
 * 之前缺这个 Bean，导致只有手工 setCreatedAt(...) 的实体能正常插入；
 * 没手工设的（如 NodeRelationEntity）会触发 PG 的 NOT NULL 约束失败。
 *
 * 命名约定：
 *   createdAt — 仅在 INSERT 时填一次
 *   updatedAt — INSERT 和 UPDATE 都填
 */
@Slf4j
@Component
public class MybatisMetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
