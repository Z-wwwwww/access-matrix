package com.platform.system.rbac.controller;

import com.platform.system.rbac.dto.MenuNode;
import com.platform.system.rbac.service.MenuQueryService;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/menu")
public class MeMenuController {

    private final MenuQueryService menuQueryService;
    private final JwtDecoder jwtDecoder;

    public MeMenuController(MenuQueryService menuQueryService, JwtDecoder jwtDecoder) {
        this.menuQueryService = menuQueryService;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/me")
    public JsonResult<List<MenuNode>> me(HttpServletRequest req) {
        String userId = extractUserId(req);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return JsonResult.ok(menuQueryService.loadUserMenuTree(userId));
    }

    private String extractUserId(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken().getSubject();
        }
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(7));
                return jwt.getSubject();
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid token");
            }
        }
        return null;
    }
}
