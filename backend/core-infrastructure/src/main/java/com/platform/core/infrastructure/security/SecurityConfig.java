package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import com.platform.core.infrastructure.web.CoreRequestContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private static final String[] PERMIT_PATHS = {
            "/health/**",
            "/auth/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final AppSecurityProperties props;

    public SecurityConfig(AppSecurityProperties props) {
        this.props = props;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthRateLimitFilter rateLimitFilter,
            CoreRequestContextFilter ctxFilter,
            ForceLogoutFilter forceLogoutFilter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Rate-limit is per-IP and runs before auth (so the IP itself
                // is what's limited even when no JWT is present).
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                // Context filter MUST run AFTER all authentication filters so
                // SecurityContextHolder is already populated when it reads the
                // JWT. Anchoring on AuthorizationFilter (the very last filter
                // in the Spring Security chain — always present regardless of
                // mode) is the simplest way to guarantee that ordering;
                // anchoring on UsernamePasswordAuthenticationFilter used to be
                // a subtle bug because BearerTokenAuthenticationFilter sits
                // AFTER UsernamePasswordAuthenticationFilter in Spring Security
                // 6/7, so ctxFilter ran with an empty SecurityContext under OIDC.
                .addFilterBefore(ctxFilter, AuthorizationFilter.class)
                // Runs right after ctxFilter (so MDC/RequestContext are set)
                // and still before AuthorizationFilter / controllers — kicks
                // land on /menu/me etc. too, not only on @RequiresPermission.
                .addFilterAfter(forceLogoutFilter, CoreRequestContextFilter.class);

        // app.security.mode controls how auth is enforced:
        //   permit-all : no auth check at all (test runs / smoke).
        //   jwt        : legacy — accept HS256 tokens signed by AdminAuthController.
        //   oidc       : accept RS256 tokens issued by an external IdP (Keycloak),
        //                verified against the IdP's JWKS endpoint. SecurityBeansConfig
        //                wires the right JwtDecoder bean based on the same property.
        if ("permit-all".equalsIgnoreCase(props.mode())) {
            http.authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
        } else {
            http
                    .authorizeHttpRequests(reg -> reg
                            .requestMatchers(PERMIT_PATHS).permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter conv = new JwtGrantedAuthoritiesConverter();
        conv.setAuthoritiesClaimName(props.jwt().authoritiesClaim());
        conv.setAuthorityPrefix("");
        JwtAuthenticationConverter jac = new JwtAuthenticationConverter();
        jac.setJwtGrantedAuthoritiesConverter(conv);
        jac.setPrincipalClaimName(props.jwt().usernameClaim());
        return jac;
    }
}
