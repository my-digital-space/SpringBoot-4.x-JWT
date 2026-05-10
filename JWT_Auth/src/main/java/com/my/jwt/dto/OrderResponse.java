package com.my.jwt.dto;

import com.my.jwt.entity.Order; // Source entity for mapping
import com.my.jwt.enums.OrderStatus; // Order lifecycle enum

import java.math.BigDecimal; // Precise monetary value
import java.time.Instant; // UTC timestamp

/**
 * Response body returned for individual or listed orders.
 * Maps an {@link Order} entity to a JSON-safe representation.
 *
 * @param id          unique order identifier
 * @param description what was ordered
 * @param totalAmount total monetary value
 * @param status      current lifecycle stage
 * @param ownerEmail  email of the user who placed the order
 * @param createdAt   UTC timestamp of order creation
 * @param updatedAt   UTC timestamp of last modification
 */
public record OrderResponse( // Immutable response DTO

        Long id, // Exposed to clients for subsequent GET/PUT/DELETE calls

        String description, // Human-readable order summary

        BigDecimal totalAmount, // Monetary value formatted as a decimal string in JSON

        OrderStatus status, // Current stage: PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED

        String ownerEmail, // Helps ADMIN/MANAGER identify which user placed the order

        Instant createdAt, // ISO-8601 timestamp thanks to Jackson config

        Instant updatedAt // ISO-8601 timestamp of the last modification
) {

    /**
     * Maps an {@link Order} entity to an {@link OrderResponse} DTO.
     *
     * @param order the entity loaded from the database
     * @return a populated response record
     */
    public static OrderResponse from(Order order) {
        // Project only the fields needed by API clients; never expose internal entity fields
        return new OrderResponse(
                order.getId(),               // Unique identifier
                order.getDescription(),      // Order description
                order.getTotalAmount(),      // Total cost
                order.getStatus(),           // Current status
                order.getUser().getEmail(),  // Owner's email; user is eagerly available here
                order.getCreatedAt(),        // Creation timestamp
                order.getUpdatedAt()         // Last-modified timestamp
        );
    }
}
