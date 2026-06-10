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
 * One option inside a managed dictionary. GLOBAL set (no {@code tenant_id}),
 * standalone like {@link DictEntity} / {@code MenuEntity}.
 *
 * <p>Lifecycle rules enforced by the service (see "字典删除策略"):
 * <ul>
 *   <li>{@link #itemValue} is <b>frozen once created</b> — business rows store it
 *       raw, so re-purposing a value would silently corrupt history.</li>
 *   <li>"删除" is normally {@link #status}=0 (disabled): hidden from new dropdowns
 *       but still resolvable for historical rows. {@code mark}=0 is hard removal.</li>
 * </ul>
 */
@Getter
@Setter
@TableName("core_dict_item")
public class DictItemEntity {

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

    /** Owning dict type ({@link DictEntity#getDictCode()}). */
    @TableField("dict_code")
    private String dictCode;

    /** Stored value (frozen once used). String form; numeric dicts store e.g. {@code "1"}. */
    @TableField("item_value")
    private String itemValue;

    /** Locale → label, raw JSON in the {@code label_i18n} jsonb column. */
    @TableField("label_i18n")
    private String labelI18n;

    @TableField("sort_no")
    private Integer sortNo;

    /** Optional Badge variant for display. */
    @TableField("css_class")
    private String cssClass;

    /** 1 = enabled (offered in dropdowns); 0 = disabled (hidden, but still resolves labels). */
    @TableField("status")
    private Integer status;

    /** Optional extra metadata, raw JSON in the {@code ext} jsonb column. */
    @TableField("ext")
    private String ext;
}
