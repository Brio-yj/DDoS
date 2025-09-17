package com.example.common.repository;

import com.example.common.domain.auth.RefreshToken;
import com.example.common.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
    void deleteByUser(User user);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
