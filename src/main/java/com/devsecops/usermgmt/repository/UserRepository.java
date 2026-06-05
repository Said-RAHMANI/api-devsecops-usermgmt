package com.devsecops.usermgmt.repository;

import com.devsecops.usermgmt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 *
 * <p>All finders use derived query methods which generate parameterized
 * SQL — they are immune to SQL injection by construction.</p>
 */
// INJ-03: To inject SQL injection, add @Query("SELECT u FROM User u WHERE u.username = '" + username + "'")
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
