package com.devsecops.usermgmt.repository;

import com.devsecops.usermgmt.entity.User;

import java.util.Optional;

/**
 * Custom finder(s) for {@link User}, implemented in {@link UserRepositoryImpl}.
 */
public interface UserRepositoryCustom {

    Optional<User> findByUsername(String username);
}
