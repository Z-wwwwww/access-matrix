package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtIssuer {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final String ISSUER = "core-service";
    public static final String CLAIM_ROLE_IDS = "rid";

    private final JwtEncoder encoder;
    private final AppSecurityProperties props;

    @Autowired
    public JwtIssuer(JwtEncoder encoder, AppSecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * Sign an access token.
     *
     * @param scopeClaim already-joined scope string. Caller decides between:
     *                   <ul>
     *                     <li>{@code "*:*"}        — super admin short-circuit</li>
     *                     <li>space-separated codes — normal case</li>
     *                     <li>{@code "__compact__"} — too many perms; resolver falls back to cache/DB</li>
     *                   </ul>
     * @param roleIds    role IDs the user currently holds, persisted as {@code rid} claim
     *                   for audit and as the lookup key on the compact path
     */
    public TokenIssue issue(String userId, String tenantId, String username,
                            String scopeClaim, List<String> roleIds) {
        Instant now = Instant.now();
        Instant exp = now.plus(ACCESS_TTL);
        String tokenId = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(tokenId)
                .issuer(ISSUER)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(exp)
                .claim(props.jwt().usernameClaim(), username)
                .claim(props.jwt().tenantClaim(), tenantId == null ? "default" : tenantId)
                .claim(props.jwt().authoritiesClaim(), scopeClaim == null ? "" : scopeClaim)
                .claim(CLAIM_ROLE_IDS, roleIds == null ? "" : String.join(",", roleIds))
                .build();

        JwsHeader header = JwsHeader.with(() -> "HS256").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenIssue(token, tokenId, now, exp, ACCESS_TTL.toSeconds());
    }

    public record TokenIssue(String token, String tokenId, Instant issuedAt, Instant expiresAt, long expiresInSec) {}
}
