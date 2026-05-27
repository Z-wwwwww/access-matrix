package com.platform.system.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.core.infrastructure.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single-use reset-password token. Reverse counterpart of
 * {@link UserInviteEntity}: minted by the SSO → password migration so
 * KC-managed users can transition back to in-house password auth without
 * locking themselves out. See V24 migration for storage shape.
 */
@Getter
@Setter
@TableName("core_password_reset_token")
public class PasswordResetTokenEntity extends BaseEntity {

    /** Business user id the reset is for. */
    @TableField("user_id")
    private String userId;

    /**
     * Snapshot of the user's Keycloak UUID at mint time. Used by the
     * acceptance endpoint to disableUser on the KC side after the password
     * is set locally. Nullable — a future "admin issues reset token manually"
     * path could mint without a KC link.
     */
    @TableField("keycloak_id")
    private String keycloakId;

    /** SHA-256 (hex) of the cleartext token. Cleartext lives ONLY in the email. */
    @TableField("token_hash")
    private String tokenHash;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** Non-null = consumed. Once set, the token cannot be reused. */
    @TableField("used_at")
    private LocalDateTime usedAt;
}
