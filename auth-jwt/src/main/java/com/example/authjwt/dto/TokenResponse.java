package com.example.authjwt.dto;

import java.util.Set;

public record TokenResponse(
        Long userId,
        String email,
        Set<String> roles,
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn
) {
}
