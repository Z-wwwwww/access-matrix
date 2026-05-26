package com.platform.core.infrastructure.mail;

import com.platform.core.infrastructure.config.properties.AppMailProperties;
import freemarker.template.Template;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails (account-opened notices, invite links). Replaces
 * the older {@code SendMailUtil} pattern (static fields + commons-email +
 * static {@code @Value} injection) with Spring Boot's standard
 * {@link JavaMailSender} + Freemarker for HTML templates.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code app.mail.enabled=false} short-circuits all sends — useful in
 *       dev / tests so a missing SMTP password doesn't propagate as an error
 *       to the caller (user creation still works, the email just doesn't go
 *       out). The intended subject + recipient still hit the log so a dev
 *       can grab a magic link from the logs in a pinch.</li>
 *   <li>{@link #sendHtmlAsync} returns a {@link CompletableFuture} so the
 *       calling transaction doesn't block on SMTP I/O. We do NOT swallow
 *       send failures inside the future — callers should hang
 *       {@code .exceptionally(...)} on it to log / surface the failure.</li>
 *   <li>Templates live under {@code classpath:/mailtemplate/} and use the
 *       Freemarker {@code .ftl} extension. The
 *       {@code spring.freemarker.template-loader-path} property points the
 *       autoconfigured {@link FreeMarkerConfigurer} there.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final FreeMarkerConfigurer freemarker;
    private final AppMailProperties props;

    public MailService(JavaMailSender sender,
                       FreeMarkerConfigurer freemarker,
                       AppMailProperties props) {
        this.sender = sender;
        this.freemarker = freemarker;
        this.props = props;
    }

    /**
     * Render a Freemarker template and send the result as an HTML email.
     * Synchronous — use {@link #sendHtmlAsync} for non-blocking sends.
     *
     * @param to           recipient email
     * @param subject      mail subject (already localised by caller)
     * @param templateName template name relative to mailtemplate/
     *                     classpath dir, including the {@code .ftl} suffix
     *                     (e.g. {@code "user-invite.ftl"})
     * @param model        template model map
     * @throws MailDeliveryException when SMTP / template processing fails
     *                     and {@code app.mail.enabled=true}
     */
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> model) {
        if (!props.enabled()) {
            log.info("[mail][disabled] would have sent '{}' to {}", subject, to);
            return;
        }
        String html = renderTemplate(templateName, model);
        MimeMessage msg = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.from(), props.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new MailDeliveryException("Failed to build mime message to " + to, e);
        }
        try {
            sender.send(msg);
            log.info("[mail] sent '{}' to {}", subject, to);
        } catch (Exception e) {
            throw new MailDeliveryException("SMTP send failed for " + to + " subject=" + subject, e);
        }
    }

    /**
     * Async wrapper around {@link #sendHtml}. Returns a future whose failure
     * is the caller's responsibility to drain — uncaught exceptions in
     * forgotten futures only land in JVM unhandled-exception channels.
     */
    public CompletableFuture<Void> sendHtmlAsync(String to, String subject, String templateName, Map<String, Object> model) {
        return CompletableFuture.runAsync(() -> sendHtml(to, subject, templateName, model));
    }

    private String renderTemplate(String templateName, Map<String, Object> model) {
        try {
            Template t = freemarker.getConfiguration().getTemplate(templateName);
            return FreeMarkerTemplateUtils.processTemplateIntoString(t, model);
        } catch (IOException | freemarker.template.TemplateException e) {
            throw new MailDeliveryException("Failed to render template " + templateName, e);
        }
    }

    /** Unchecked wrapper around SMTP / template failures, surfaced to callers. */
    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Auto-config hook for {@link AppMailProperties} — keeps the @ConfigurationProperties
     * registration co-located with its only consumer so a future module-split
     * doesn't lose the wiring.
     */
    @Configuration
    @EnableConfigurationProperties(AppMailProperties.class)
    static class Bindings {}
}
