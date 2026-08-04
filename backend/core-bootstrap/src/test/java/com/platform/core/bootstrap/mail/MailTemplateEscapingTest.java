package com.platform.core.bootstrap.mail;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTML mail templates must escape their interpolations.
 *
 * <p>The templates are HTML and the loader uses the {@code .ftl} suffix, so
 * Freemarker's default output format applies and {@code ${...}} is emitted
 * VERBATIM unless {@code output_format} says otherwise — no template declares
 * {@code <#ftl output_format="HTML">} either.
 *
 * <p>That matters because the model carries genuinely attacker-controlled values:
 * {@code user-break-glass-used} renders the login's {@code User-Agent} and client
 * IP, and that email goes to the account owner precisely to tell them "if this
 * wasn't you, rotate now" — with a red "rotate" button. Unescaped, a crafted
 * User-Agent can rewrite or blank the whole alert (or repoint that button),
 * defeating the one control the email exists to provide.
 *
 * <p>Fix is the {@code spring.freemarker.settings.output_format: HTML} entry in
 * {@code application.yml}. These tests pin both halves: that the setting is still
 * there, and that with it the hostile value really is escaped while the templates
 * still render (i.e. none of them needed {@code ?no_esc}).
 */
class MailTemplateEscapingTest {

    private static final String HOSTILE =
            "</td></tr></table><style>body{display:none}</style><a href=\"http://evil\">x</a>";

    private static Configuration cfg;

    @BeforeAll
    static void setUp() {
        cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setClassLoaderForTemplateLoading(
                MailTemplateEscapingTest.class.getClassLoader(), "/mailtemplate/");
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLocalizedLookup(false);
        // The setting under test — mirrors application.yml.
        cfg.setOutputFormat(freemarker.core.HTMLOutputFormat.INSTANCE);
    }

    private static Map<String, Object> breakGlassModel() {
        Map<String, Object> m = new HashMap<>();
        m.put("appName", "Access Matrix");
        m.put("logoUrl", "https://app.example.com/access_matrix_logo.png");
        m.put("username", "admin");
        m.put("displayName", "Admin");
        m.put("loginAt", "2026-08-04 12:00:00");
        m.put("clientIp", HOSTILE);
        m.put("userAgent", HOSTILE);
        m.put("tenantId", "demo");
        m.put("rotateUrl", "https://app.example.com/login");
        m.put("supportEmail", "support@example.com");
        return m;
    }

    private static String render(String name, Map<String, Object> model) throws Exception {
        Template t = cfg.getTemplate(name);
        StringWriter out = new StringWriter();
        t.process(model, out);
        return out.toString();
    }

    @Test
    void breakGlassAlert_escapesTheAttackerControlledUserAgentAndIp() throws Exception {
        String html = render("user-break-glass-used.en.ftl", breakGlassModel());

        assertThat(html)
                .as("the hostile markup must not survive as markup")
                .doesNotContain("<style>")
                .doesNotContain("<a href=\"http://evil\">");
        assertThat(html)
                .as("it must appear as escaped text instead")
                .contains("&lt;style&gt;");
        // The alert's own structure must still be intact — that's what the attack
        // was trying to break.
        assertThat(html).contains("Sign in via SSO and rotate");
    }

    @Test
    void urlsInHrefStayUsableWhenEscaped() throws Exception {
        Map<String, Object> m = breakGlassModel();
        m.put("rotateUrl", "https://app.example.com/login?a=1&b=2");

        String html = render("user-break-glass-used.en.ftl", m);

        // & → &amp; is the CORRECT form inside an HTML attribute; browsers and mail
        // clients resolve it back to the original URL.
        assertThat(html).contains("https://app.example.com/login?a=1&amp;b=2");
        assertThat(html).doesNotContain("?a=1&b=2\"");
    }

    /** Every mail template must render under HTML output format — none may need ?no_esc. */
    @Test
    void allTemplatesRenderUnderHtmlOutputFormat() throws Exception {
        Map<String, Object> m = breakGlassModel();
        m.put("inviteUrl", "https://app.example.com/invite/tok");
        m.put("loginUrl", "https://app.example.com/login");
        m.put("resetUrl", "https://app.example.com/reset-password/tok");
        m.put("tempPassword", "Tmp-" + HOSTILE);
        m.put("expiresIn", "7");
        m.put("tenantName", "Demo Tenant");

        for (String base : new String[]{"user-invite", "user-direct-welcome",
                "user-password-reset", "user-break-glass-used"}) {
            for (String lang : new String[]{"en", "ja_JP", "zh_CN", "zh_TW", "ko_KR"}) {
                String name = base + "." + lang + ".ftl";
                String html = render(name, m);
                assertThat(html).as("%s rendered empty", name).isNotBlank();
                assertThat(html).as("%s leaked raw markup", name).doesNotContain("<style>");
            }
        }
    }

    /**
     * Drift guard: the render tests above configure the output format themselves, so
     * they would keep passing if the real config lost the setting. Assert the actual
     * {@code application.yml} still carries it.
     */
    @Test
    void applicationYamlStillEnablesHtmlOutputFormat() throws Exception {
        String yml = new String(getClass().getResourceAsStream("/application.yml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(yml)
                .as("spring.freemarker.settings.output_format must stay HTML — without it "
                        + "${...} in the HTML mail templates is emitted verbatim")
                .contains("output_format: HTML");
    }
}
