package com.example.authsession.dto;

import java.util.Set;

public record UserResponse(Long id, String email, Set<String> roles) {
}
