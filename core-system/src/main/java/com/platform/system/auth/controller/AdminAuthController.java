package com.platform.system.auth.controller;

import com.platform.system.auth.dto.ResetPasswordRequest;
import com.platform.system.auth.dto.UnlockRequest;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final UserMapper userMapper;
    private final AccountLockoutService lockoutService;
    private final PasswordPolicyService passwordPolicy;
    private final PasswordEncoder encoder;
    private final JwtDecoder jwtDecoder;

    public AdminAuthController(UserMapper userMapper, AccountLockoutService lockoutService,
                               PasswordPolicyService passwordPolicy, PasswordEncoder encoder,
                               JwtDecoder jwtDecoder) {
        this.userMapper = userMapper;
        this.lockoutService = lockoutService;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
        this.jwtDecoder = jwtDecoder;
    }

    @PostMapping("/unlock")
    public JsonResult<Void> unlock(@Valid @RequestBody UnlockRequest body, HttpServletRequest req) {
        requireAuthority(req, "auth:unlock");
        UserEntity user = userMapper.findByIdentifier(body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        lockoutService.reset(body.username());
        user.setStatus(1);
        userMapper.updateById(user);
        return JsonResult.ok();
    }

    @PostMapping("/reset-password")
    public JsonResult<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest req) {
        requireAuthority(req, "auth:reset-password");
        passwordPolicy.validate(body.newPassword());
        UserEntity user = userMapper.findByIdentifier(body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        user.setPasswordHash(encoder.encode(body.newPassword()));
        userMapper.updateById(user);
        return JsonResult.ok();
    }

    private void requireAuthority(HttpServletRequest req, String required) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(7));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid token");
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope == null) scope = "";
        List<String> auths = Arrays.asList(scope.split("\\s+"));
        if (!auths.contains("*:*") && !auths.contains(required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Missing required authority: " + required);
        }
    }
}
