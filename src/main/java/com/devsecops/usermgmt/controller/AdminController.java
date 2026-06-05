package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.dto.PageResponse;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration endpoints — protected at both the URL level
 * ({@code /api/admin/**}) and at the method level via
 * {@link PreAuthorize}, providing defense in depth.
 *
 * <p>Parameter validation (page bounds, size cap) is enforced in
 * {@link UserService#getAllUsersPaged} to avoid AOP proxy conflicts
 * with Spring Security's method security.</p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Privileged administrator operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Paginated user list.
     *
     * <p>Example: {@code GET /api/admin/users?page=0&size=20&sort=username}</p>
     *
     * @param page zero-based page index (default 0)
     * @param size page size, capped at 100 by the service (default 20)
     * @param sort field name to sort by ascending (default "id")
     */
    @Operation(summary = "List users with pagination (admin only)",
               parameters = {
                   @Parameter(name = "page", description = "Zero-based page index (default 0)"),
                   @Parameter(name = "size", description = "Page size, max 100 (default 20)"),
                   @Parameter(name = "sort", description = "Sort field, e.g. username (default id)")
               })
    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort) {
        return ResponseEntity.ok(userService.getAllUsersPaged(page, size, sort));
    }

    @Operation(summary = "Delete a user by id (admin only)")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.adminDeleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
