package com.devsecops.usermgmt.service;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.LoginRequest;
import com.devsecops.usermgmt.dto.RefreshTokenRequest;
import com.devsecops.usermgmt.dto.RegisterRequest;
import com.devsecops.usermgmt.dto.TokenRefreshResponse;
import com.devsecops.usermgmt.entity.RefreshToken;
import com.devsecops.usermgmt.entity.Role;
import com.devsecops.usermgmt.entity.User;
import com.devsecops.usermgmt.mapper.UserMapper;
import com.devsecops.usermgmt.repository.UserRepository;
import com.devsecops.usermgmt.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for registration and login.
 *
 * <p>Passwords are stored as BCrypt hashes only. New accounts are
 * always created with {@link Role#ROLE_USER} regardless of the request
 * payload — preventing privilege escalation through the public API.</p>
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public JwtResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered new user '{}'", saved.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));
        String accessToken = jwtTokenProvider.generateToken((UserDetails) authentication.getPrincipal());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(saved.getUsername());

        return UserMapper.toJwtResponse(accessToken, refreshToken.getToken(), saved);
    }

    @Transactional
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(userDetails);

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user is missing from the database"));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());

        return UserMapper.toJwtResponse(accessToken, refreshToken.getToken(), user);
    }

    /**
     * Issues a new access token and rotates the refresh token.
     *
     * @throws IllegalArgumentException if the refresh token is invalid or expired
     */
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldToken);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(newRefreshToken.getUser().getUsername())
                .password("")
                .authorities(newRefreshToken.getUser().getRole().name())
                .build();
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        log.info("Refreshed tokens for user '{}'", newRefreshToken.getUser().getUsername());

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .build();
    }

    /**
     * Revokes the refresh token for the authenticated user (logout).
     */
    @Transactional
    public void logout(String username) {
        refreshTokenService.revokeByUsername(username);
        log.info("User '{}' logged out", username);
    }
}
