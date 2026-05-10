package com.my.jwt.enums;

/**
 * Lifecycle stages of a customer {@link com.my.jwt.entity.Order}.
 */
public enum OrderStatus {

    /** Order has been placed but not yet processed. */
    PENDING,

    /** Order is actively being processed. */
    PROCESSING,

    /** Order has been dispatched to the carrier. */
    SHIPPED,

    /** Order has been delivered to the customer. */
    DELIVERED,

    /** Order was cancelled before fulfilment. */
    CANCELLED
}
