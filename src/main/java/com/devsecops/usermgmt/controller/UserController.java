package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.dto.UpdateUserRequest;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated user endpoints. The current principal is resolved via
 * {@link AuthenticationPrincipal} so that ownership checks operate on
 * the cryptographically verified identity, never on a client-provided
 * value.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Self-service and id-scoped user operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the currently authenticated user")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(userService.getCurrentUser(currentUser.getUsername()));
    }

    @Operation(summary = "Update the currently authenticated user")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                userService.updateCurrentUser(currentUser.getUsername(), request));
    }

    @Operation(summary = "Get a user by id (owner or admin)")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(userService.getUserById(id, currentUser.getUsername()));
    }

    @Operation(summary = "Update a user by id (owner or admin)")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(
                userService.updateUser(id, request, currentUser.getUsername()));
    }

    @Operation(summary = "Delete a user by id (owner or admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {
        userService.deleteUser(id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
