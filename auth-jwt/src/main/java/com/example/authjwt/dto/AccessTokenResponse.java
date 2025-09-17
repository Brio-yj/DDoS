package com.example.authjwt.dto;

public record AccessTokenResponse(
        String accessToken,
        long accessTokenExpiresIn
) {
}
