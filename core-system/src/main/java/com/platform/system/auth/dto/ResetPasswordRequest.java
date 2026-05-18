package com.platform.system.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String username,
        @NotBlank String newPassword) {}
