package com.devsecops.usermgmt.entity;

/**
 * Application roles. The {@code ROLE_} prefix is required so that Spring
 * Security's {@code hasRole("ADMIN")} matches the granted authority
 * {@code ROLE_ADMIN} stored in the database.
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
