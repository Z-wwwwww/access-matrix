package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outgoing-mail settings used by {@code MailService}. Distinct from
 * {@code spring.mail.*} (which configures the underlying
 * {@code JavaMailSender}) — these are *our* business-side knobs.
 *
 * @param enabled  master kill-switch. When false, {@code MailService}
 *                 short-circuits and logs the intended send — useful in
 *                 dev / tests so a missing SMTP password doesn't block
 *                 the rest of the flow.
 * @param from     SMTP From: address. Should match (or be authorised by)
 *                 {@code spring.mail.username}.
 * @param fromName Human-readable From: name; shows in mail clients.
 * @param baseUrl  Public base URL of the frontend, used for building
 *                 absolute links inside templates (e.g. invite acceptance
 *                 page). Defaults to {@code http://localhost:5273}.
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        boolean enabled,
        String from,
        String fromName,
        String baseUrl) {

    public AppMailProperties {
        if (from == null     || from.isBlank())     from     = "noreply@localhost";
        if (fromName == null || fromName.isBlank()) fromName = "Access Matrix";
        if (baseUrl == null  || baseUrl.isBlank())  baseUrl  = "http://localhost:5273";
    }
}
