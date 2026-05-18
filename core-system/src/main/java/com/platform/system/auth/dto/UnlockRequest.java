package com.platform.system.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UnlockRequest(@NotBlank String username) {}
