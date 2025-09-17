package com.example.common.security;

import java.util.List;

public record JwtPrincipal(Long userId, String email, List<String> roles) {
}
