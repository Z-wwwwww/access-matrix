package com.platform.core.infrastructure.security;

import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieService {

    private final AppSecurityProperties.RefreshCookie cookieProps;

    public RefreshCookieService(AppSecurityProperties props) {
        this.cookieProps = props.refreshCookie();
    }

    public void writeCookie(HttpServletResponse resp, String token) {
        Duration ttl = RefreshTokenStore.REFRESH_TTL;
        StringBuilder sb = new StringBuilder();
        sb.append(cookieProps.name()).append('=').append(token);
        sb.append("; Path=").append(cookieProps.path());
        sb.append("; HttpOnly");
        sb.append("; Max-Age=").append(ttl.toSeconds());
        sb.append("; SameSite=").append(cookieProps.sameSite());
        if (cookieProps.secure()) {
            sb.append("; Secure");
        }
        resp.addHeader("Set-Cookie", sb.toString());
    }

    public String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (cookieProps.name().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public void clearCookie(HttpServletResponse resp) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookieProps.name()).append('=');
        sb.append("; Path=").append(cookieProps.path());
        sb.append("; HttpOnly");
        sb.append("; Max-Age=0");
        sb.append("; SameSite=").append(cookieProps.sameSite());
        if (cookieProps.secure()) {
            sb.append("; Secure");
        }
        resp.addHeader("Set-Cookie", sb.toString());
    }
}
