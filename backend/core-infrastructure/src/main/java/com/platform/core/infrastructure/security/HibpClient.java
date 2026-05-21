package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HibpClient {

    private static final Logger log = LoggerFactory.getLogger(HibpClient.class);

    private final AppSecurityProperties.PasswordPolicy cfg;
    private final HttpClient http;

    public HibpClient(AppSecurityProperties props) {
        this.cfg = props.passwordPolicy();
        this.http = HttpClient.newBuilder()
                .connectTimeout(cfg.hibpTimeout())
                .build();
    }

    /**
     * Returns true if the password has been seen in a breach (>=1 occurrence).
     * On HIBP unreachable: returns !failOpenOnHibpError.
     */
    public boolean isCompromised(String plain) {
        if (!cfg.hibpEnabled() || plain == null || plain.isEmpty()) return false;
        try {
            String sha1 = sha1Hex(plain).toUpperCase();
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.hibpBaseUrl() + "/range/" + prefix))
                    .timeout(cfg.hibpTimeout())
                    .header("Add-Padding", "true")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("HIBP returned status {}", resp.statusCode());
                return !cfg.failOpenOnHibpError();
            }
            for (String line : resp.body().split("\r?\n")) {
                int sep = line.indexOf(':');
                if (sep < 0) continue;
                String hashSuffix = line.substring(0, sep).trim();
                if (hashSuffix.equalsIgnoreCase(suffix)) {
                    String countStr = line.substring(sep + 1).trim();
                    try {
                        return Integer.parseInt(countStr) > 0;
                    } catch (NumberFormatException ignored) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("HIBP query failed: {}", e.getMessage());
            return !cfg.failOpenOnHibpError();
        }
    }

    private static String sha1Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
