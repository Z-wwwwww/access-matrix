package com.platform.system.platform.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Central registry row for one business tenant. Owned by the system
 * tenant (every row has {@code tenant_id='system'}); managed exclusively
 * by PLATFORM_ADMIN via the platform console.
 *
 * <p>The {@code tenant_code} field is the canonical business-tenant id:
 * it equals the Keycloak realm name AND the value of {@code tenant_id}
 * on every business-tenant row across {@code core_auth_user},
 * {@code core_oplog}, etc. Renaming a tenant is not currently supported
 * because changing the code would require a multi-table data move and
 * a Keycloak realm rename — out of scope for this iteration.
 */
@Getter
@Setter
@TableName("core_tenant")
public class TenantEntity extends BaseEntity {

    /** Canonical business tenant id. Lowercase RFC1035 label. */
    @TableField("tenant_code")
    private String tenantCode;

    /** Human-readable name shown in admin consoles and login pages. */
    @TableField("display_name")
    private String displayName;

    /** Primary admin's email — used by the platform UI to know who to invite first. */
    @TableField("contact_email")
    private String contactEmail;

    /**
     * 1 = active (KC realm enabled, users can sign in)
     * 0 = suspended (KC realm disabled — typically used for billing-blocked tenants)
     */
    @TableField("status")
    private Integer status;

    /**
     * Free-form JSONB blob for billing plan / feature flags / quotas.
     * Schema-less by design — platform extensions own their keys.
     * Read as a raw String here; consumers parse as needed.
     */
    @TableField("meta")
    private String meta;
}
