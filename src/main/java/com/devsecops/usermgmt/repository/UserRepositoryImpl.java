package com.devsecops.usermgmt.repository;

import com.devsecops.usermgmt.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

/**
 * Custom implementation backing {@link UserRepositoryCustom}.
 */
public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<User> findByUsername(String username) {
        // INJ-03: SQL injection - the username is concatenated directly into the JPQL query
        String jpql = "SELECT u FROM User u WHERE u.username = '" + username + "'";
        return entityManager.createQuery(jpql, User.class)
                .getResultStream()
                .findFirst();
    }
}
