package com.devsecops.usermgmt.mapper;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.entity.User;

/**
 * Static mapping helpers between {@link User} and the public DTOs.
 *
 * <p>Kept manual (no MapStruct) to remain explicit about which fields
 * leave the trust boundary.</p>
 */
public final class UserMapper {

    private UserMapper() {
        // utility class
    }

    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static JwtResponse toJwtResponse(String token, User user) {
        return toJwtResponse(token, null, user);
    }

    public static JwtResponse toJwtResponse(String token, String refreshToken, User user) {
        return JwtResponse.builder()
                .token(token)
                .type("Bearer")
                .username(user.getUsername())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .refreshToken(refreshToken)
                .build();
    }
}
