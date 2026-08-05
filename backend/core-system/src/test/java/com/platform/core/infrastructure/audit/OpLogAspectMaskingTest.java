package com.platform.core.infrastructure.audit;

import com.platform.core.infrastructure.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code core_oplog.request_body} is a permanent, admin-readable record of every
 * audited request body — so the aspect's password masking is the only thing
 * standing between an admin typing a password and that password sitting in the
 * audit table forever. Two audited endpoints carry one in their body:
 * {@code POST /user} (DIRECT provisioning, the admin types it) and
 * {@code POST /me/break-glass-password}.
 *
 * <p>The mask used to be {@code "password"\s*:\s*"[^"]*"}. JSON string values may
 * contain an ESCAPED quote, and {@code [^"]*} stops at the backslash — so for a
 * password containing {@code "} the match ended mid-value: the whole remainder
 * was written out in cleartext and the row's JSON was left malformed. Since
 * {@code app.security.password-policy.require-symbol} defaults to true, a quote
 * is an entirely ordinary character for a user to pick.
 */
class OpLogAspectMaskingTest {

    /** Minimal stand-in for the audited create-user body. */
    record CreateBody(String username, String password, String email) {}

    private static OpLogAspect aspect() {
        return new OpLogAspect(emptyProvider(), JsonMapper.builder().build(),
                new ClientIpResolver(false));
    }

    /** ObjectProvider stub — the aspect only calls getIfAvailable() on it, never here. */
    private static ObjectProvider<OpLogSink> emptyProvider() {
        return new ObjectProvider<>() {
            @Override public OpLogSink getObject(Object... args) { return null; }
            @Override public OpLogSink getObject() { return null; }
            @Override public OpLogSink getIfAvailable() { return null; }
            @Override public OpLogSink getIfUnique() { return null; }
            @Override public void ifAvailable(Consumer<OpLogSink> c) { }
            @Override public void ifUnique(Consumer<OpLogSink> c) { }
            @Override public OpLogSink getIfAvailable(Supplier<OpLogSink> s) { return s.get(); }
        };
    }

    @Test
    void masks_a_plain_password() {
        String json = aspect().serialiseArgs(new Object[]{
                new CreateBody("alice", "Sup3rSecret!", "a@example.com")});

        assertThat(json).doesNotContain("Sup3rSecret!");
        assertThat(json).contains("\"password\":\"***\"");
        // Non-secret fields must survive — the audit row still has to be useful.
        assertThat(json).contains("alice").contains("a@example.com");
    }

    @Test
    void masks_a_password_containing_a_double_quote() {
        // The regression. Old pattern matched only up to the escaped quote, so
        // everything after it landed in the audit row in cleartext.
        String json = aspect().serialiseArgs(new Object[]{
                new CreateBody("alice", "Aa1!\"secret", "a@example.com")});

        assertThat(json)
                .as("the tail of a quote-containing password leaked into the audit body")
                .doesNotContain("secret");
        assertThat(json).contains("\"password\":\"***\"");
    }

    @Test
    void masks_a_password_ending_in_a_backslash() {
        // A trailing backslash is escaped as \\ — the value alternation must
        // consume the pair rather than treat the second one as an escape of the
        // closing quote (which would run the match past the end of the value).
        String json = aspect().serialiseArgs(new Object[]{
                new CreateBody("alice", "Aa1!pass\\", "a@example.com")});

        assertThat(json).contains("\"password\":\"***\"");
        assertThat(json).contains("alice");
    }

    @Test
    void the_masked_body_is_still_valid_json() {
        // A half-consumed value left `{"password":"***"secret","email":…}` behind,
        // which no consumer of core_oplog can parse.
        String json = aspect().serialiseArgs(new Object[]{
                new CreateBody("alice", "Aa1!\"secret", "a@example.com")});

        assertThat(JsonMapper.builder().build().readTree(json).get("password").asString())
                .isEqualTo("***");
    }

    @Test
    void masks_every_declared_secret_field_name_case_insensitively() {
        record Body(String newPassword, String oldPassword, String PWD) {}
        String json = aspect().serialiseArgs(new Object[]{
                new Body("new\"one", "old\"two", "pwd\"three")});

        assertThat(json).doesNotContain("one").doesNotContain("two").doesNotContain("three");
    }

    @Test
    void leaves_a_body_without_secrets_untouched() {
        record Body(String title, String note) {}
        String json = aspect().serialiseArgs(new Object[]{new Body("hello", "world")});

        assertThat(json).contains("hello").contains("world").doesNotContain("***");
    }
}
