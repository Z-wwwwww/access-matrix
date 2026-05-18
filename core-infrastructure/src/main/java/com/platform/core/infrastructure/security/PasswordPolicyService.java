package com.platform.core.infrastructure.security;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.infrastructure.config.properties.AppSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyService {

    private final AppSecurityProperties.PasswordPolicy cfg;
    private final HibpClient hibp;

    public PasswordPolicyService(AppSecurityProperties props, HibpClient hibp) {
        this.cfg = props.passwordPolicy();
        this.hibp = hibp;
    }

    public void validate(String password) {
        if (password == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password is required");
        }
        int len = password.length();
        if (len < cfg.minLength()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Password must be at least " + cfg.minLength() + " characters");
        }
        if (len > cfg.maxLength()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Password must be at most " + cfg.maxLength() + " characters");
        }
        boolean hasDigit = false, hasUpper = false, hasLower = false, hasSymbol = false;
        for (int i = 0; i < len; i++) {
            char c = password.charAt(i);
            if (Character.isDigit(c)) hasDigit = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else hasSymbol = true;
        }
        if (cfg.requireDigit() && !hasDigit)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password must contain a digit");
        if (cfg.requireUpper() && !hasUpper)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password must contain an uppercase letter");
        if (cfg.requireLower() && !hasLower)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password must contain a lowercase letter");
        if (cfg.requireSymbol() && !hasSymbol)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Password must contain a symbol");

        if (cfg.hibpEnabled() && hibp.isCompromised(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Password has appeared in a public breach corpus — choose a different one");
        }
    }
}
