package com.my.jwt.enums;

/**
 * Defines the three application roles used for Spring Security authorization.
 *
 * <ul>
 *   <li>{@link #ADMIN}   – full system access</li>
 *   <li>{@link #MANAGER} – operational access to orders and products</li>
 *   <li>{@link #USER}    – read-only access to own orders</li>
 * </ul>
 *
 * <p>Stored as a {@code VARCHAR} in the database via JPA {@code @Enumerated(EnumType.STRING)}.</p>
 */
public enum Role {

    /** Administrator with full system access. */
    ADMIN,

    /** Manager with operational access to orders and products. */
    MANAGER,

    /** Regular user with read-only access to their own orders. */
    USER
}
