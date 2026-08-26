package com.platform.core.infrastructure.mail;

import com.platform.core.infrastructure.config.properties.AppMailProperties;
import freemarker.template.Template;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails (account-opened notices, invite links). Replaces
 * the older {@code SendMailUtil} pattern (static fields + commons-email +
 * static {@code @Value} injection) with Spring Boot's standard
 * {@link JavaMailSender} + Freemarker for HTML templates +
 * Spring's {@link MessageSource} for localised subjects.
 *
 * <p>Localisation:
 * <ul>
 *   <li>Templates are looked up as
 *       {@code mailtemplate/<baseName>.<localeTag>.ftl} (e.g.
 *       {@code user-invite.ja_JP.ftl}); when the locale-specific file is
 *       missing, falls back to the {@code ja_JP} default.</li>
 *   <li>Subjects come from {@code i18n/mail[_locale].properties} via
 *       Spring's {@link ReloadableResourceBundleMessageSource}, so adding
 *       a new locale is just dropping in a new properties file.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /** Repo-default locale — every fallback (missing template / missing subject) lands here. */
    private static final Locale FALLBACK_LOCALE = Locale.JAPAN;
    private static final String FALLBACK_TAG    = "ja_JP";

    private final JavaMailSender sender;
    private final FreeMarkerConfigurer freemarker;
    private final AppMailProperties props;
    private final MessageSource subjectMessages;

    public MailService(JavaMailSender sender,
                       FreeMarkerConfigurer freemarker,
                       AppMailProperties props) {
        this.sender = sender;
        this.freemarker = freemarker;
        this.props = props;
        this.subjectMessages = buildSubjectMessageSource();
    }

    private static MessageSource buildSubjectMessageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/mail");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setDefaultLocale(FALLBACK_LOCALE);
        ms.setFallbackToSystemLocale(false);
        ms.setCacheSeconds(-1);  // cache forever (dev-style hot-reload is via app restart)
        return ms;
    }

    /**
     * Render a localised Freemarker template and send the result as HTML mail.
     * Synchronous — use {@link #sendHtmlAsync} for non-blocking sends.
     *
     * @param to            recipient email
     * @param locale        UI locale of the recipient — drives template + subject choice
     * @param subjectKey    MessageSource key under {@code i18n/mail*.properties}
     *                      (e.g. {@code "user-invite.subject"})
     * @param subjectArgs   args injected into the subject pattern (MessageFormat)
     * @param templateBase  base name of the template (e.g. {@code "user-invite"});
     *                      actual file resolved as {@code <base>.<localeTag>.ftl}
     * @param model         template model map
     */
    public void sendHtml(String to,
                         Locale locale,
                         String subjectKey,
                         Object[] subjectArgs,
                         String templateBase,
                         Map<String, Object> model) {
        if (!props.enabled()) {
            log.info("[mail][disabled] would have sent template={} locale={} to {}",
                    templateBase, localeTag(normalise(locale)), to);
            return;
        }
        Locale loc = normalise(locale);
        String subject = resolveSubject(subjectKey, subjectArgs, loc);
        // Brand logo for the email header — external PNG under the app's base URL
        // (SVG is rendered by the SPA but not by Gmail/Outlook). Caller may override
        // by pre-setting logoUrl in the model.
        if (model != null) model.putIfAbsent("logoUrl", props.baseUrl() + "/access_matrix_logo.png");
        String html    = renderTemplate(templateBase, loc, model);

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
            log.info("[mail] sent '{}' (locale={}) to {}", subject, localeTag(loc), to);
        } catch (Exception e) {
            throw new MailDeliveryException("SMTP send failed for " + to + " subject=" + subject, e);
        }
    }

    /**
     * Async wrapper around {@link #sendHtml}. Returns a future whose failure is
     * the caller's to drain — but we ALSO log any failure here so a fire-and-
     * forget caller (the common case: invite / reset emails) never loses an
     * SMTP error silently. Without this, a failed send on the async thread
     * completes the future exceptionally and, if nobody calls join()/get(),
     * vanishes — leaving "email never arrived" with nothing in the logs.
     * {@code whenComplete} does not swallow the exception, so callers that DO
     * drain the future still see it.
     */
    public CompletableFuture<Void> sendHtmlAsync(String to,
                                                 Locale locale,
                                                 String subjectKey,
                                                 Object[] subjectArgs,
                                                 String templateBase,
                                                 Map<String, Object> model) {
        return CompletableFuture
                .runAsync(() -> sendHtml(to, locale, subjectKey, subjectArgs, templateBase, model))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null
                                ? ex.getCause() : ex;
                        log.error("[mail] async send FAILED: template={} to={} : {}",
                                templateBase, to, cause.toString(), cause);
                    }
                });
    }

    private String resolveSubject(String key, Object[] args, Locale locale) {
        try {
            return subjectMessages.getMessage(key, args, locale);
        } catch (NoSuchMessageException e) {
            // Fall back to the repo-default locale; if even that's missing, surface
            // the raw key to the recipient so the gap is visible (better than an
            // empty subject line that confuses spam filters).
            try {
                return subjectMessages.getMessage(key, args, FALLBACK_LOCALE);
            } catch (NoSuchMessageException ee) {
                log.warn("[mail] subject key '{}' missing in i18n/mail*.properties — using raw key", key);
                return key;
            }
        }
    }

    private String renderTemplate(String baseName, Locale locale, Map<String, Object> model) {
        // First try the locale-specific template, fall back to FALLBACK_LOCALE
        // so a missing translation still renders SOMETHING readable rather than
        // throwing — the recipient's mail is more important than perfect i18n.
        String preferred = baseName + "." + localeTag(locale) + ".ftl";
        String fallback  = baseName + "." + FALLBACK_TAG + ".ftl";
        Template t;
        try {
            t = freemarker.getConfiguration().getTemplate(preferred);
        } catch (IOException primary) {
            try {
                t = freemarker.getConfiguration().getTemplate(fallback);
                log.warn("[mail] template '{}' not found, falling back to '{}'", preferred, fallback);
            } catch (IOException fb) {
                throw new MailDeliveryException(
                        "Both " + preferred + " and fallback " + fallback + " missing under mailtemplate/", fb);
            }
        }
        try {
            return FreeMarkerTemplateUtils.processTemplateIntoString(t, model);
        } catch (freemarker.template.TemplateException | IOException e) {
            throw new MailDeliveryException("Failed to render template " + t.getName(), e);
        }
    }

    /**
     * The locales this project actually ships subjects and templates for, in
     * within-language preference order (so a region-less {@code zh} lands on
     * Simplified rather than Traditional).
     */
    private static final List<Locale> SUPPORTED = List.of(
            Locale.JAPAN,                // ja_JP — also FALLBACK_LOCALE
            Locale.ENGLISH,              // en
            Locale.SIMPLIFIED_CHINESE,   // zh_CN
            Locale.TRADITIONAL_CHINESE,  // zh_TW
            Locale.of("ko", "KR"));      // ko_KR

    /**
     * Map whatever locale the request carried onto one of {@link #SUPPORTED}.
     *
     * <p>Without this the recipient's language was decided by an exact filename
     * match, and the tags the SPA sends do not line up with the tags the files
     * use. {@code request.js} sends {@code Accept-language: en-US} for English
     * and {@code ko} for Korean, so {@link #localeTag} produced {@code en_US}
     * and {@code ko} — neither of which names a template
     * ({@code user-invite.en.ftl} / {@code user-invite.ko_KR.ftl}), so
     * {@link #renderTemplate} fell through to the Japanese body. The SUBJECT did
     * not fall through the same way — {@code MessageSource} resolves
     * {@code en_US → en} on its own — so an English recipient got an English
     * subject line on a Japanese email, and a Korean recipient got Japanese on
     * both ({@code mail_ko.properties} does not exist either). Only {@code ja_JP}
     * and {@code zh_CN} happened to line up.
     *
     * <p>Resolving once, here, also keeps the two halves from disagreeing again:
     * {@link #sendHtml} normalises before it touches either.
     *
     * <p>Order: exact match wins; then any locale of the same language (so
     * {@code en_US → en}, {@code ko → ko_KR}, {@code zh_HK → zh_CN}); then the
     * project fallback.
     */
    static Locale normalise(Locale requested) {
        if (requested == null) return FALLBACK_LOCALE;
        for (Locale s : SUPPORTED) {
            if (s.equals(requested)) return s;
        }
        for (Locale s : SUPPORTED) {
            if (s.getLanguage().equals(requested.getLanguage())) return s;
        }
        return FALLBACK_LOCALE;
    }

    /**
     * Normalises a {@link Locale} to the {@code lang_REGION} string we use as
     * filename suffix. Examples: {@code Locale.JAPAN -> "ja_JP"},
     * {@code Locale.ENGLISH -> "en"}, {@code Locale.SIMPLIFIED_CHINESE -> "zh_CN"}.
     * Callers feed it a {@link #normalise}d locale — a raw request locale would
     * produce a tag no template file carries.
     */
    static String localeTag(Locale locale) {
        if (locale == null) return FALLBACK_TAG;
        String lang   = locale.getLanguage();
        String region = locale.getCountry();
        return (region == null || region.isEmpty()) ? lang : (lang + "_" + region);
    }

    /** Unchecked wrapper around SMTP / template failures, surfaced to callers. */
    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) { super(message, cause); }
    }

    @Configuration
    @EnableConfigurationProperties(AppMailProperties.class)
    static class Bindings {}
}
