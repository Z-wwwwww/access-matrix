package com.platform.core.infrastructure.mail;

import com.platform.core.infrastructure.config.properties.AppMailProperties;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every locale the app offers must actually receive its own email — subject AND
 * body.
 *
 * <p>The template is chosen by filename ({@code <base>.<tag>.ftl}), and the tags
 * the SPA sends did not line up with the tags the files use.
 * {@code request.js} sends {@code Accept-language: en-US} for English and
 * {@code ko} for Korean, so the tag came out {@code en_US} / {@code ko} while the
 * files are {@code user-invite.en.ftl} / {@code user-invite.ko_KR.ftl}.
 * {@code renderTemplate} caught the miss and fell back to Japanese — quietly, and
 * only the body: subjects go through {@code MessageSource}, which resolves
 * {@code en_US → en} by itself. An English recipient therefore got an English
 * subject line on a Japanese email. A Korean recipient got Japanese on both
 * ({@code mail_ko.properties} does not exist; only {@code mail_ko_KR}). Only
 * {@code ja_JP} and {@code zh_CN} happened to line up.
 *
 * <p>The last test is the one that matters: it walks the SUPPORTED set through
 * the same {@code normalise → localeTag} pair the runtime uses and demands the
 * resources exist, so a new locale (or a renamed template) cannot silently
 * degrade to Japanese again.
 */
class MailLocaleResolutionTest {

    /** Template bases shipped under {@code mailtemplate/}. */
    private static final List<String> TEMPLATE_BASES = List.of(
            "user-invite", "user-direct-welcome", "user-password-reset", "user-break-glass-used");

    /** Subject keys shipped under {@code i18n/mail*.properties}. */
    private static final List<String> SUBJECT_KEYS = List.of(
            "user-invite.subject", "user-direct-welcome.subject", "user-account-reset.subject",
            "user-password-reset.subject", "user-break-glass-used.subject");

    private static final List<Locale> SUPPORTED = List.of(
            Locale.JAPAN, Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE,
            Locale.TRADITIONAL_CHINESE, Locale.of("ko", "KR"));

    @ParameterizedTest(name = "{0} resolves to {1}")
    @CsvSource({
            // What request.js actually puts on the wire, and what it must become.
            "en-US,   en",       // English UI → Accept-language: en-US
            "ko,      ko_KR",    // Korean UI  → Accept-language: ko
            "ja-JP,   ja_JP",
            "zh-CN,   zh_CN",
            "zh-TW,   zh_TW",    // only reachable since LANG_MAP gained zh_TW
            // Anything else: same language if we ship it, else the project default.
            "zh-HK,   zh_CN",
            "en-GB,   en",
            "ja,      ja_JP",
            "fr-FR,   ja_JP",
            "xx,      ja_JP"
    })
    void requestLocalesMapOntoAShippedTag(String acceptLanguage, String expectedTag) {
        Locale requested = Locale.forLanguageTag(acceptLanguage);

        assertThat(MailService.localeTag(MailService.normalise(requested))).isEqualTo(expectedTag);
    }

    @Test
    @DisplayName("a null locale falls back rather than blowing up")
    void nullLocaleFallsBack() {
        assertThat(MailService.localeTag(MailService.normalise(null))).isEqualTo("ja_JP");
    }

    @Test
    @DisplayName("an exact match wins over a same-language one (zh_TW must not become zh_CN)")
    void exactMatchBeatsLanguageMatch() {
        assertThat(MailService.normalise(Locale.TRADITIONAL_CHINESE))
                .isEqualTo(Locale.TRADITIONAL_CHINESE);
    }

    @Test
    @DisplayName("every supported locale has its own template and subject — nothing degrades to Japanese")
    void everySupportedLocaleHasItsOwnResources() {
        ReloadableResourceBundleMessageSource subjects = new ReloadableResourceBundleMessageSource();
        subjects.setBasename("classpath:i18n/mail");
        subjects.setDefaultEncoding(StandardCharsets.UTF_8.name());
        subjects.setDefaultLocale(Locale.JAPAN);
        subjects.setFallbackToSystemLocale(false);

        String japanese = subjects.getMessage(SUBJECT_KEYS.get(0), new Object[]{"[X]"}, Locale.JAPAN);

        for (Locale locale : SUPPORTED) {
            String tag = MailService.localeTag(MailService.normalise(locale));

            for (String base : TEMPLATE_BASES) {
                String path = "/mailtemplate/" + base + "." + tag + ".ftl";
                assertThat(getClass().getResource(path))
                        .as("%s — without it renderTemplate silently sends the Japanese body", path)
                        .isNotNull();
            }
            for (String key : SUBJECT_KEYS) {
                assertThat(subjects.getMessage(key, new Object[]{"[X]"}, locale))
                        .as("subject %s for %s", key, tag)
                        .isNotBlank();
            }
            if (!Locale.JAPAN.equals(locale)) {
                assertThat(subjects.getMessage(SUBJECT_KEYS.get(0), new Object[]{"[X]"}, locale))
                        .as("%s must not be resolving to the Japanese default bundle", tag)
                        .isNotEqualTo(japanese);
            }
        }
    }

    // ── the wiring, not just the helpers ────────────────────────────────────
    //
    // The tests above pin normalise/localeTag in isolation; they stay green even
    // if sendHtml goes back to handing renderTemplate the RAW request locale,
    // which is exactly how the bug looked. These two drive the real MailService
    // and record which template file it actually asked for.

    /** Captures instead of talking to SMTP. */
    private static final class CapturingSender extends JavaMailSenderImpl {
        MimeMessage captured;
        @Override public void send(MimeMessage m) { this.captured = m; }
    }

    /** Records every template name FreeMarker is asked to load. */
    private static final class RecordingLoader implements TemplateLoader {
        final List<String> requested = new ArrayList<>();
        private final TemplateLoader delegate = new ClassTemplateLoader(
                MailLocaleResolutionTest.class, "/mailtemplate/");

        @Override public Object findTemplateSource(String name) throws IOException {
            requested.add(name);
            return delegate.findTemplateSource(name);
        }
        @Override public long getLastModified(Object s) { return delegate.getLastModified(s); }
        @Override public Reader getReader(Object s, String enc) throws IOException {
            return delegate.getReader(s, enc);
        }
        @Override public void closeTemplateSource(Object s) throws IOException {
            delegate.closeTemplateSource(s);
        }
    }

    /** Send one invite and report which template files FreeMarker was asked for. */
    private static List<String> templatesRequestedFor(Locale requestLocale) {
        RecordingLoader loader = new RecordingLoader();
        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_34);
        cfg.setTemplateLoader(loader);
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        cfg.setLocalizedLookup(false);   // the suffix IS the locale here
        cfg.setOutputFormat(freemarker.core.HTMLOutputFormat.INSTANCE);
        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
        configurer.setConfiguration(cfg);

        MailService mail = new MailService(new CapturingSender(), configurer,
                new AppMailProperties(true, "noreply@test", "Access Matrix", "https://app.test"));

        Map<String, Object> model = new HashMap<>();
        model.put("appName", "Access Matrix");
        model.put("username", "alice");
        model.put("displayName", "Alice");
        model.put("inviteUrl", "https://app.test/invite/TOKEN");
        model.put("expiresIn", "7");
        model.put("supportEmail", "support@test");

        mail.sendHtml("alice@test", requestLocale,
                "user-invite.subject", new Object[]{"[X]"}, "user-invite", model);
        return loader.requested;
    }

    @Test
    @DisplayName("an English recipient gets the English BODY, not just an English subject")
    void englishRequestLocaleLoadsTheEnglishTemplate() {
        // request.js sends Accept-language: en-US for the English UI.
        List<String> asked = templatesRequestedFor(Locale.forLanguageTag("en-US"));

        assertThat(asked).contains("user-invite.en.ftl");
        assertThat(asked)
                .as("falling back to Japanese is the bug — the English template exists")
                .doesNotContain("user-invite.ja_JP.ftl");
    }

    @Test
    @DisplayName("a Korean recipient gets the Korean template (Accept-language: ko carries no region)")
    void koreanRequestLocaleLoadsTheKoreanTemplate() {
        List<String> asked = templatesRequestedFor(Locale.forLanguageTag("ko"));

        assertThat(asked).contains("user-invite.ko_KR.ftl");
        assertThat(asked).doesNotContain("user-invite.ja_JP.ftl");
    }

    @Test
    @DisplayName("Traditional Chinese is reachable now that LANG_MAP maps it")
    void traditionalChineseLoadsItsOwnTemplate() {
        List<String> asked = templatesRequestedFor(Locale.forLanguageTag("zh-TW"));

        assertThat(asked).contains("user-invite.zh_TW.ftl");
        assertThat(asked).doesNotContain("user-invite.zh_CN.ftl", "user-invite.ja_JP.ftl");
    }
}
