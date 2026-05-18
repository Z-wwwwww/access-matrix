package com.platform.core.bootstrap.auth.controller;

import com.platform.core.bootstrap.auth.dto.UserInfoResponse;
import com.platform.core.bootstrap.auth.entity.UserEntity;
import com.platform.core.bootstrap.auth.mapper.UserMapper;
import com.platform.core.bootstrap.auth.service.AuthService;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserMapper userMapper;
    private final JwtDecoder jwtDecoder;
    private final AppSecurityProperties props;

    public UserController(UserMapper userMapper, JwtDecoder jwtDecoder, AppSecurityProperties props) {
        this.userMapper = userMapper;
        this.jwtDecoder = jwtDecoder;
        this.props = props;
    }

    @GetMapping("/me")
    public JsonResult<UserInfoResponse> me(HttpServletRequest req) {
        String userId = extractUserId(req);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        UserEntity u = userMapper.selectById(userId);
        if (u == null || u.getMark() == null || u.getMark() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        UserInfoResponse resp = new UserInfoResponse(
                u.getId(),
                u.getUsername(),
                u.getUserNo(),
                u.getEmail(),
                u.getDisplayName(),
                u.getTenantId(),
                AuthService.parseJsonArray(u.getRoles()),
                AuthService.parseJsonArray(u.getAuthorities())
        );
        return JsonResult.ok(resp);
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
