package com.platform.system.auth.controller;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.entity.UserInviteEntity;
import com.platform.system.auth.service.InviteTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pre-auth invite-acceptance endpoints. Both GET and POST are reachable
 * without a session — the cleartext invite token is the proof of identity.
 *
 * <p>Only registered when {@code app.security.mode=oidc} — the legacy
 * password path doesn't use Keycloak for credential storage, so an
 * invite landing page wouldn't have anywhere to set the password.
 */
@RestController
@RequestMapping("/auth/invite")
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class InviteController {

    private final InviteTokenService inviteTokenService;
    private final KeycloakUserService keycloakUserService;
    private final PasswordPolicyService passwordPolicy;
    private final AppMailProperties mailProps;

    public InviteController(InviteTokenService inviteTokenService,
                            KeycloakUserService keycloakUserService,
                            PasswordPolicyService passwordPolicy,
                            AppMailProperties mailProps) {
        this.inviteTokenService = inviteTokenService;
        this.keycloakUserService = keycloakUserService;
        this.passwordPolicy = passwordPolicy;
        this.mailProps = mailProps;
    }

    /**
     * Renders no-PII confirmation that an invite is still claimable. Used by
     * the frontend landing page before showing the "set your password" form
     * so the user doesn't fill out the form only to discover the token is
     * expired / used.
     */
    @GetMapping("/{token}")
    public JsonResult<Map<String, Object>> probe(@PathVariable String token) {
        UserInviteEntity row = inviteTokenService.peek(token);
        if (row == null) {
            // Intentionally one error code for all of "not found / expired /
            // used" so we don't leak which case it is (mild defence against
            // token-enumeration probing).
            throw new BusinessException(ErrorCode.NOT_FOUND, "Invite is no longer valid");
        }
        return JsonResult.ok(Map.of(
                "valid",     true,
                "tenantId",  row.getTenantId(),
                "expiresAt", row.getExpiresAt().toString()
        ));
    }

    /**
     * Accept the invite: validate password against policy, consume the
     * (single-use) token, and push the new permanent password into Keycloak.
     * Returns success on completion — the user can then go log in normally.
     */
    @PostMapping("/{token}")
    public JsonResult<Map<String, Object>> accept(@PathVariable String token,
                                                  @Valid @RequestBody AcceptInviteRequest req) {
        passwordPolicy.validate(req.password());

        // Consume FIRST so a slow / retried HTTP request can't double-spend.
        UserInviteEntity row = inviteTokenService.consume(token);

        // Push the permanent password into Keycloak. If this fails the token
        // has already been marked used — admin will have to issue a fresh
        // invite. This is the safer side of the trade-off (better to need
        // a new invite than to have a reusable token after a partial failure).
        keycloakUserService.setPassword(row.getTenantId(), row.getKeycloakId(),
                req.password(), /* temporary = */ false);

        return JsonResult.ok(Map.of(
                "loginUrl", mailProps.baseUrl() + "/login"
        ));
    }

    public record AcceptInviteRequest(
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
