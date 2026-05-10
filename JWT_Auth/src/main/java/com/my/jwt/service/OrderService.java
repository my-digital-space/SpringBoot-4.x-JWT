package com.my.jwt.service;

import com.my.jwt.dto.OrderRequest; // Request DTO for create/update
import com.my.jwt.dto.OrderResponse; // Response DTO returned to the client
import com.my.jwt.entity.Order; // Order JPA entity
import com.my.jwt.entity.User; // User JPA entity
import com.my.jwt.enums.OrderStatus; // Order lifecycle enum
import com.my.jwt.enums.Role; // Role enum for access control logic
import com.my.jwt.exception.ApiException; // Application exception with HTTP status
import com.my.jwt.repository.OrderRepository; // CRUD operations on orders table
import com.my.jwt.repository.UserRepository; // Load user by email for ownership checks
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.http.HttpStatus; // HTTP status constants
import org.springframework.stereotype.Service; // Spring service bean
import org.springframework.transaction.annotation.Transactional; // DB transaction management

import java.util.List; // List of order responses

/**
 * Service containing all order management business logic.
 *
 * <p>Role-based access control:</p>
 * <ul>
 *   <li>{@code USER} – can only view, create, update, and delete their own orders</li>
 *   <li>{@code MANAGER} / {@code ADMIN} – can view, update, and delete any order</li>
 * </ul>
 */
@Service // Spring-managed bean
@RequiredArgsConstructor // Lombok: inject all final fields
public class OrderService {

    /** CRUD operations for Order entities. */
    private final OrderRepository orderRepository; // Injected repository

    /** Used to load the full User entity from the email stored in the JWT principal. */
    private final UserRepository userRepository; // Injected repository

    // -------------------------------------------------------
    // List orders
    // -------------------------------------------------------

    /**
     * Returns all orders visible to the currently authenticated user.
     * USER role sees only their own orders; MANAGER/ADMIN see all.
     *
     * @param currentUserEmail email extracted from the JWT by the controller
     * @return list of order response DTOs
     */
    @Transactional(readOnly = true) // Read-only; no writes in this method
    public List<OrderResponse> getOrders(String currentUserEmail) {
        User currentUser = loadUser(currentUserEmail); // Load user entity from DB

        // ADMIN and MANAGER see all orders; USER sees only their own
        if (currentUser.getRole() == Role.USER) {
            // Fetch only orders belonging to this user
            return orderRepository.findByUser(currentUser).stream() // SELECT * FROM orders WHERE user_id = ?
                    .map(OrderResponse::from) // Map entity → DTO
                    .toList(); // Collect to an unmodifiable list
        }

        // ADMIN / MANAGER: return all orders in the system
        return orderRepository.findAll().stream() // SELECT * FROM orders
                .map(OrderResponse::from) // Map entity → DTO
                .toList(); // Collect to an unmodifiable list
    }

    // -------------------------------------------------------
    // Get one order
    // -------------------------------------------------------

    /**
     * Returns a single order by ID.
     * USER role can only retrieve their own order.
     *
     * @param orderId          database ID of the order
     * @param currentUserEmail email of the requesting user
     * @return the matching order response DTO
     */
    @Transactional(readOnly = true) // Read-only transaction
    public OrderResponse getOrderById(Long orderId, String currentUserEmail) {
        Order order = loadOrder(orderId); // Load order or throw 404
        User currentUser = loadUser(currentUserEmail); // Load requesting user

        enforceOwnershipForUser(order, currentUser); // Throw 403 if USER tries to read another's order
        return OrderResponse.from(order); // Map to DTO and return
    }

    // -------------------------------------------------------
    // Create order
    // -------------------------------------------------------

    /**
     * Creates a new order owned by the currently authenticated user.
     *
     * @param request          validated order data
     * @param currentUserEmail email of the creating user
     * @return the persisted order as a response DTO
     */
    @Transactional // Write transaction
    public OrderResponse createOrder(OrderRequest request, String currentUserEmail) {
        User currentUser = loadUser(currentUserEmail); // Load the creating user

        Order order = Order.builder()
                .user(currentUser) // Assign ownership to the requesting user
                .description(request.description()) // From request DTO
                .totalAmount(request.totalAmount()) // From request DTO
                .status(OrderStatus.PENDING) // New orders always start as PENDING
                .build();

        Order saved = orderRepository.save(order); // INSERT INTO orders ...
        return OrderResponse.from(saved); // Return the persisted entity as a DTO
    }

    // -------------------------------------------------------
    // Update order
    // -------------------------------------------------------

    /**
     * Updates an existing order's description and total amount.
     * USER role can only update their own orders.
     *
     * @param orderId          database ID of the order to update
     * @param request          validated update data
     * @param currentUserEmail email of the requesting user
     * @return the updated order as a response DTO
     */
    @Transactional // Write transaction
    public OrderResponse updateOrder(Long orderId, OrderRequest request, String currentUserEmail) {
        Order order = loadOrder(orderId); // Load order or throw 404
        User currentUser = loadUser(currentUserEmail); // Load requesting user

        enforceOwnershipForUser(order, currentUser); // Block USER from updating another's order

        order.setDescription(request.description()); // Apply new description
        order.setTotalAmount(request.totalAmount()); // Apply new total amount
        // @PreUpdate on the entity automatically refreshes updatedAt

        Order updated = orderRepository.save(order); // UPDATE orders SET ...
        return OrderResponse.from(updated); // Return updated DTO
    }

    // -------------------------------------------------------
    // Delete order
    // -------------------------------------------------------

    /**
     * Deletes an order by ID.
     * USER role can only delete their own orders.
     *
     * @param orderId          database ID of the order to delete
     * @param currentUserEmail email of the requesting user
     */
    @Transactional // Write transaction
    public void deleteOrder(Long orderId, String currentUserEmail) {
        Order order = loadOrder(orderId); // Load order or throw 404
        User currentUser = loadUser(currentUserEmail); // Load requesting user

        enforceOwnershipForUser(order, currentUser); // Block USER from deleting another's order

        orderRepository.delete(order); // DELETE FROM orders WHERE id = ?
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /** Loads an order by ID or throws 404. */
    private Order loadOrder(Long orderId) {
        return orderRepository.findById(orderId) // SELECT * FROM orders WHERE id = ?
                .orElseThrow(() -> new ApiException("Order not found with id: " + orderId, HttpStatus.NOT_FOUND));
    }

    /** Loads a user by email or throws 404. */
    private User loadUser(String email) {
        return userRepository.findByEmail(email) // SELECT * FROM users WHERE email = ?
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
    }

    /**
     * If the current user has the USER role, verifies they own the order.
     * MANAGER and ADMIN bypass this check.
     *
     * @param order       the order to check
     * @param currentUser the requesting user
     * @throws ApiException 403 if a USER tries to access another user's order
     */
    private void enforceOwnershipForUser(Order order, User currentUser) {
        if (currentUser.getRole() == Role.USER // Only enforce for USER role
                && !order.getUser().getId().equals(currentUser.getId())) { // Different owner
            throw new ApiException("Access denied: you do not own this order", HttpStatus.FORBIDDEN); // 403
        }
    }
}
