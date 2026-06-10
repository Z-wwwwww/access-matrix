package com.platform.system.dict.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A managed dictionary <i>type</i> (a named list of options). Like
 * {@code core_rbac_menu} this is a single GLOBAL set — NOT tenant-scoped — so it
 * does <b>not</b> extend {@code BaseEntity}; {@code core_dict} has no
 * {@code tenant_id} column and is listed in {@code MybatisPlusConfig.TENANT_EXCLUDED_TABLES}.
 * Items live in {@code core_dict_item}, linked by {@link #dictCode}.
 */
@Getter
@Setter
@TableName("core_dict")
public class DictEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableLogic(value = "1", delval = "0")
    @TableField(value = "mark", fill = FieldFill.INSERT)
    private Integer mark;

    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private String createUser;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private OffsetDateTime createTime;

    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Version
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updateTime;

    // ── business fields ──────────────────────────────────────────────

    /** Stable lookup key, e.g. {@code "gender"}. Unique among active rows. */
    @TableField("dict_code")
    private String dictCode;

    /** Locale → display name of the dict type, raw JSON in the {@code name_i18n} jsonb column. */
    @TableField("name_i18n")
    private String nameI18n;

    /** 1 = system/protected (cannot be deleted, items cannot be removed); 0 = ordinary. */
    @TableField("builtin")
    private Integer builtin;

    @TableField("remark")
    private String remark;
}
