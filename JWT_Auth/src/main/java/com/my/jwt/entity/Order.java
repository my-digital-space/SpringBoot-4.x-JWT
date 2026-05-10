package com.my.jwt.entity;

import com.my.jwt.enums.OrderStatus; // Enum representing the lifecycle stage of an order
import jakarta.persistence.*; // JPA annotations
import lombok.*; // Lombok annotations

import java.math.BigDecimal; // Precise monetary amount for order total
import java.time.Instant; // UTC timestamp for order creation/update times

/**
 * JPA entity representing a customer order.
 *
 * <p>Access control rules:</p>
 * <ul>
 *   <li>USER – can only see their own orders</li>
 *   <li>MANAGER / ADMIN – can see all orders</li>
 * </ul>
 */
@Entity // Marks this class as a JPA-managed table
@Table(name = "orders") // Explicit table name (avoids conflict with SQL reserved word ORDER)
@Getter // Lombok: generates getters
@Setter // Lombok: generates setters
@NoArgsConstructor // Lombok: no-args constructor required by JPA
@AllArgsConstructor // Lombok: all-args constructor for use with @Builder
@Builder // Lombok: fluent builder pattern
public class Order {

    /** Primary key; auto-incremented by the database. */
    @Id // Primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    private Long id; // Unique order identifier

    /** The user who placed this order. */
    @ManyToOne(fetch = FetchType.LAZY) // Many orders per user; lazy-load to avoid N+1
    @JoinColumn(name = "user_id", nullable = false) // FK column in the orders table
    private User user; // Owner of this order

    /** Human-readable description of what was ordered. */
    @Column(nullable = false) // NOT NULL
    private String description; // e.g. "2x Widget Pro, 1x Gadget Deluxe"

    /** Total monetary amount for this order. */
    @Column(nullable = false, precision = 12, scale = 2) // Up to 10 digits + 2 decimal places
    private BigDecimal totalAmount; // Stored as DECIMAL(12,2) in MySQL

    /** Current lifecycle stage of the order. */
    @Enumerated(EnumType.STRING) // Persists enum name as VARCHAR
    @Column(nullable = false) // NOT NULL
    private OrderStatus status; // e.g. PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED

    /** Timestamp when the order was first created (UTC). */
    @Column(nullable = false, updatable = false) // NOT NULL, never updated after insert
    private Instant createdAt; // Set in @PrePersist

    /** Timestamp of the last update to this order (UTC). */
    @Column(nullable = false) // NOT NULL
    private Instant updatedAt; // Updated in @PreUpdate

    /** Sets createdAt and updatedAt before the first database INSERT. */
    @PrePersist // JPA lifecycle callback — fires before INSERT
    protected void onCreate() {
        createdAt = Instant.now(); // Capture creation timestamp
        updatedAt = Instant.now(); // Initialize updatedAt equal to createdAt
    }

    /** Refreshes updatedAt before every database UPDATE. */
    @PreUpdate // JPA lifecycle callback — fires before UPDATE
    protected void onUpdate() {
        updatedAt = Instant.now(); // Capture the current modification timestamp
    }
}
