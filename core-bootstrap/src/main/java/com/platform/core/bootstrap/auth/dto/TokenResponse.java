package com.platform.core.bootstrap.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType) {

    public static TokenResponse of(String access, String refresh, long expiresIn) {
        return new TokenResponse(access, refresh, expiresIn, "Bearer");
    }
}
