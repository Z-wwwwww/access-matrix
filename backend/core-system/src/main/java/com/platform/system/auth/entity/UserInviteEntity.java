package com.platform.system.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single-use invite token row backing the "admin invites user" provisioning
 * flow. See V22 migration for storage shape.
 */
@Getter
@Setter
@TableName("core_user_invite")
public class UserInviteEntity extends BaseEntity {

    /** Business user id this invite is for. */
    @TableField("user_id")
    private String userId;

    /** Keycloak UUID — denormalised so the acceptance endpoint doesn't need a 2nd lookup. */
    @TableField("keycloak_id")
    private String keycloakId;

    /** SHA-256 (hex) of the cleartext token. Cleartext lives ONLY in the recipient's email. */
    @TableField("token_hash")
    private String tokenHash;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** Non-null = consumed. Once set, the token cannot be reused. */
    @TableField("used_at")
    private LocalDateTime usedAt;
}
