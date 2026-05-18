package com.platform.core.bootstrap.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UnlockRequest(@NotBlank String username) {}
