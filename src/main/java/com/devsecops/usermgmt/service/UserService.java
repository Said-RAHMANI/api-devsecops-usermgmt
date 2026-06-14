package com.devsecops.usermgmt.service;

import com.devsecops.usermgmt.dto.PageResponse;
import com.devsecops.usermgmt.dto.UpdateUserRequest;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.entity.Role;
import com.devsecops.usermgmt.entity.User;
import com.devsecops.usermgmt.exception.AccessDeniedException;
import com.devsecops.usermgmt.exception.ResourceNotFoundException;
import com.devsecops.usermgmt.mapper.UserMapper;
import com.devsecops.usermgmt.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * User-centric business logic with explicit ownership / role checks
 * (defense against BOLA — Broken Object Level Authorization).
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ---------- self endpoints ----------

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String currentUsername) {
        User user = loadByUsername(currentUsername);
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(String currentUsername, UpdateUserRequest request) {
        User user = loadByUsername(currentUsername);
        applyUpdates(user, request);
        return UserMapper.toResponse(userRepository.save(user));
    }

    // ---------- id-based endpoints (ownership-checked) ----------

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id, String currentUsername) {
        User user = loadById(id);
        // verifyOwnershipOrAdmin(user, currentUsername);  // INJ-04
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, String currentUsername) {
        User user = loadById(id);
        // verifyOwnershipOrAdmin(user, currentUsername);  // INJ-04
        applyUpdates(user, request);
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = loadById(id);
        // verifyOwnershipOrAdmin(user, currentUsername);  // INJ-04
        userRepository.delete(user);
        log.info("User '{}' deleted by '{}'", user.getUsername(), currentUsername);
    }

    // ---------- admin endpoints ----------

    /** Non-paginated list kept for backward compatibility with existing tests. */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    /**
     * Paginated user list with validation of page parameters.
     *
     * @param page zero-based page index (default 0)
     * @param size page size, capped at 100 (default 20)
     * @param sort field to sort by (default "id")
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsersPaged(int page, int size, String sort) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        int effectiveSize = Math.min(Math.max(size, 1), 100);
        String effectiveSort = (sort != null && !sort.isBlank()) ? sort : "id";

        Pageable pageable = PageRequest.of(page, effectiveSize,
                Sort.by(Sort.Direction.ASC, effectiveSort));
        Page<User> userPage = userRepository.findAll(pageable);

        return PageResponse.of(userPage, UserMapper::toResponse);
    }

    @Transactional
    public void adminDeleteUser(Long id) {
        User user = loadById(id);
        userRepository.delete(user);
        log.info("Admin deleted user '{}'", user.getUsername());
    }

    // ---------- helpers ----------

    private User loadById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
    }

    private User loadByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with username: " + username));
    }

    /**
     * Allows the operation only when the caller is the resource owner
     * or has the {@code ROLE_ADMIN} authority.
     */
    // INJ-04: To inject BOLA, remove ownership verification
    private void verifyOwnershipOrAdmin(User target, String currentUsername) {
        User caller = loadByUsername(currentUsername);
        boolean isAdmin = caller.getRole() == Role.ROLE_ADMIN;
        boolean isOwner = caller.getId().equals(target.getId());
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException(
                    "You do not have permission to access this resource");
        }
    }

    private void applyUpdates(User user, UpdateUserRequest request) {
        if (StringUtils.hasText(request.getUsername())
                && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            }
            user.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email is already registered");
            }
            user.setEmail(request.getEmail());
        }
    }
}
