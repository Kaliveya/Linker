package com.sean.linker.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_module")
public class ProjectModuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 枚举键，如 LOGIN / PAYMENT / PERMISSION，ParseAgent prompt 中传入 */
    private String code;

    /** 显示名，如 登录 / 支付 / 权限 */
    private String name;
}