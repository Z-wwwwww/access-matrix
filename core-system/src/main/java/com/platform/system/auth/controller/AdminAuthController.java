package com.platform.system.auth.controller;

import com.platform.system.auth.dto.ResetPasswordRequest;
import com.platform.system.auth.dto.UnlockRequest;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.common.security.RequiresPermission;
import com.platform.core.infrastructure.security.AccountLockoutService;
import com.platform.core.infrastructure.security.PasswordPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final UserMapper userMapper;
    private final AccountLockoutService lockoutService;
    private final PasswordPolicyService passwordPolicy;
    private final PasswordEncoder encoder;

    public AdminAuthController(UserMapper userMapper, AccountLockoutService lockoutService,
                               PasswordPolicyService passwordPolicy, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.lockoutService = lockoutService;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
    }

    @PostMapping("/unlock")
    @RequiresPermission("auth:unlock")
    public JsonResult<Void> unlock(@Valid @RequestBody UnlockRequest body) {
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
    @RequiresPermission("auth:reset-password")
    public JsonResult<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordPolicy.validate(body.newPassword());
        UserEntity user = userMapper.findByIdentifier(body.username());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        user.setPasswordHash(encoder.encode(body.newPassword()));
        userMapper.updateById(user);
        return JsonResult.ok();
    }
}
