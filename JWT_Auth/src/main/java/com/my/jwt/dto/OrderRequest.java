package com.my.jwt.dto;

import jakarta.validation.constraints.DecimalMin; // Minimum decimal value constraint
import jakarta.validation.constraints.NotBlank; // Rejects null / blank strings
import jakarta.validation.constraints.NotNull; // Rejects null values

import java.math.BigDecimal; // Precise monetary value

/**
 * Request body for creating or updating an order.
 *
 * @param description human-readable summary of items ordered
 * @param totalAmount total monetary value of the order
 */
public record OrderRequest( // Immutable request DTO

        @NotBlank(message = "Order description is required") // Rejects null / whitespace
        String description, // What was ordered; e.g. "2x Widget Pro"

        @NotNull(message = "Total amount is required") // Rejects null
        @DecimalMin(value = "0.01", message = "Total amount must be greater than zero") // Must be positive
        BigDecimal totalAmount // Order value in the application's base currency
) {}
