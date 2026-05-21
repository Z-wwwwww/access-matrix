package com.platform.system.auth.controller;

import com.platform.system.auth.dto.LoginRequest;
import com.platform.system.auth.dto.RefreshRequest;
import com.platform.system.auth.dto.TokenResponse;
import com.platform.system.auth.service.AuthService;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.security.RefreshCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService cookieService;

    public AuthController(AuthService authService, RefreshCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public JsonResult<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest httpReq,
                                            HttpServletResponse httpResp) {
        AuthService.LoginResult result = authService.login(req.username(), req.password(), httpReq);
        cookieService.writeCookie(httpResp, result.tokens().refreshToken());
        return JsonResult.ok(result.tokens());
    }

    @PostMapping("/refresh")
    public JsonResult<TokenResponse> refresh(@RequestBody(required = false) RefreshRequest body,
                                              HttpServletRequest httpReq,
                                              HttpServletResponse httpResp) {
        String token = (body != null && body.refreshToken() != null && !body.refreshToken().isBlank())
                ? body.refreshToken()
                : cookieService.readCookie(httpReq);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token missing");
        }
        TokenResponse tokens = authService.refresh(token);
        cookieService.writeCookie(httpResp, tokens.refreshToken());
        return JsonResult.ok(tokens);
    }

    @PostMapping("/logout")
    public JsonResult<Void> logout(@RequestBody(required = false) RefreshRequest body,
                                   HttpServletRequest httpReq,
                                   HttpServletResponse httpResp) {
        String token = (body != null && body.refreshToken() != null && !body.refreshToken().isBlank())
                ? body.refreshToken()
                : cookieService.readCookie(httpReq);
        authService.logout(token);
        cookieService.clearCookie(httpResp);
        return JsonResult.ok();
    }
}
