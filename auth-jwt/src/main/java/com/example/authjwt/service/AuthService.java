package com.example.authjwt.service;

import com.example.authjwt.dto.AccessTokenResponse;
import com.example.authjwt.dto.LoginRequest;
import com.example.authjwt.dto.SignupRequest;
import com.example.authjwt.dto.TokenResponse;
import com.example.common.domain.auth.RefreshToken;
import com.example.common.domain.user.Role;
import com.example.common.domain.user.User;
import com.example.common.repository.RefreshTokenRepository;
import com.example.common.repository.RoleRepository;
import com.example.common.repository.UserRepository;
import com.example.common.security.JwtProperties;
import com.example.common.security.JwtTokenService;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public User registerUser(SignupRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            throw new IllegalArgumentException("Email already exists");
        });
        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER is not initialized"));
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(roleUser);
        return userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = ((com.example.authjwt.security.JwtUserPrincipal) authentication.getPrincipal()).getUser();
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse issueTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), roles.stream().toList());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setToken(refreshToken);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenValiditySeconds()));
        refreshTokenRepository.save(entity);

        return new TokenResponse(
                user.getId(),
                user.getEmail(),
                roles,
                accessToken,
                jwtProperties.getAccessTokenValiditySeconds(),
                refreshToken,
                jwtProperties.getRefreshTokenValiditySeconds());
    }

    @Transactional
    public AccessTokenResponse refresh(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }
        if (!jwtTokenService.isRefreshTokenValid(token)) {
            throw new IllegalArgumentException("Refresh token invalid");
        }
        User user = refreshToken.getUser();
        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getName).toList());
        return new AccessTokenResponse(accessToken, jwtProperties.getAccessTokenValiditySeconds());
    }
}
