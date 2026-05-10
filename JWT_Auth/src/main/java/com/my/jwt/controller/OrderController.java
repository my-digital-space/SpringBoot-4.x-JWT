package com.my.jwt.controller;

import com.my.jwt.dto.OrderRequest; // Request DTO for create / update
import com.my.jwt.dto.OrderResponse; // Response DTO returned to clients
import com.my.jwt.service.OrderService; // Order business logic
import io.swagger.v3.oas.annotations.Operation; // Swagger: documents an operation
import io.swagger.v3.oas.annotations.Parameter; // Swagger: documents a path/query parameter
import io.swagger.v3.oas.annotations.media.Content; // Swagger: response body content descriptor
import io.swagger.v3.oas.annotations.media.Schema; // Swagger: links content to a DTO schema
import io.swagger.v3.oas.annotations.responses.ApiResponse; // Swagger: single response entry
import io.swagger.v3.oas.annotations.responses.ApiResponses; // Swagger: groups ApiResponse entries
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // Swagger: marks endpoint as protected
import io.swagger.v3.oas.annotations.tags.Tag; // Swagger: groups endpoints under a tag
import jakarta.validation.Valid; // Triggers Bean Validation on @RequestBody
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.http.HttpStatus; // HTTP status constants
import org.springframework.http.ResponseEntity; // Wraps response + status
import org.springframework.security.access.prepost.PreAuthorize; // Role-based method security
import org.springframework.security.core.annotation.AuthenticationPrincipal; // Injects authenticated user
import org.springframework.security.core.userdetails.UserDetails; // Authenticated user contract
import org.springframework.web.bind.annotation.*; // REST annotations

import java.util.List; // List of order responses

/**
 * REST controller for order management endpoints.
 *
 * <p>All endpoints require a valid JWT ({@code Authorization: Bearer <token>}).
 * Role-based access is enforced at both the HTTP security layer and the service layer:</p>
 * <ul>
 *   <li>{@code GET /api/orders}        – all roles; USER sees own orders only</li>
 *   <li>{@code GET /api/orders/{id}}   – all roles; USER can only fetch their own</li>
 *   <li>{@code POST /api/orders}       – all roles</li>
 *   <li>{@code PUT /api/orders/{id}}   – all roles; USER can only update their own</li>
 *   <li>{@code DELETE /api/orders/{id}} – ADMIN and MANAGER only</li>
 * </ul>
 */
@RestController // REST controller; @ResponseBody implied on all methods
@RequestMapping("/api/v1/orders") // Base path for all order endpoints
@RequiredArgsConstructor // Lombok: inject OrderService
@SecurityRequirement(name = "bearerAuth") // All endpoints in this controller require a JWT (Swagger UI)
@Tag(name = "Orders", description = "CRUD operations for customer orders (role-based access control enforced)") // Swagger group
public class OrderController {

    /** Order management business logic. */
    private final OrderService orderService; // Injected service

    // -------------------------------------------------------
    // GET /api/orders
    // -------------------------------------------------------

    /**
     * Lists orders visible to the authenticated user.
     * USER role: own orders only. MANAGER/ADMIN: all orders.
     *
     * @param currentUser injected by Spring Security from the JWT principal
     * @return list of order response DTOs
     */
    @GetMapping // GET /api/orders
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')") // All authenticated roles may call this
    @Operation(
            summary = "List orders",
            description = "Returns all orders for ADMIN/MANAGER, or only the caller's own orders for USER role."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<List<OrderResponse>> getOrders(
            @AuthenticationPrincipal UserDetails currentUser // Spring Security injects the authenticated user
    ) {
        // Pass the caller's email to the service for ownership filtering
        return ResponseEntity.ok(orderService.getOrders(currentUser.getUsername())); // getUsername() = email
    }

    // -------------------------------------------------------
    // GET /api/orders/{id}
    // -------------------------------------------------------

    /**
     * Fetches a single order by ID.
     * USER role: returns 403 if the order belongs to a different user.
     *
     * @param id          path variable — the order's database ID
     * @param currentUser injected authenticated user
     * @return the matching order DTO
     */
    @GetMapping("/{id}") // GET /api/orders/{id}
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')") // All roles may attempt; service enforces ownership
    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "USER accessing another user's order", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Database ID of the order") @PathVariable Long id, // Path variable
            @AuthenticationPrincipal UserDetails currentUser // Authenticated caller
    ) {
        return ResponseEntity.ok(orderService.getOrderById(id, currentUser.getUsername())); // 200 + DTO
    }

    // -------------------------------------------------------
    // POST /api/orders
    // -------------------------------------------------------

    /**
     * Creates a new order owned by the authenticated user.
     *
     * @param request     validated order data
     * @param currentUser authenticated caller (becomes the order owner)
     * @return 201 Created with the persisted order DTO
     */
    @PostMapping // POST /api/orders
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')") // All roles can place orders
    @ResponseStatus(HttpStatus.CREATED) // Default status
    @Operation(summary = "Create a new order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request, // Validate and bind request body
            @AuthenticationPrincipal UserDetails currentUser // Authenticated order owner
    ) {
        OrderResponse created = orderService.createOrder(request, currentUser.getUsername()); // Delegate
        return ResponseEntity.status(HttpStatus.CREATED).body(created); // 201 + body
    }

    // -------------------------------------------------------
    // PUT /api/orders/{id}
    // -------------------------------------------------------

    /**
     * Updates an existing order.
     * USER role: returns 403 if the order belongs to a different user.
     *
     * @param id          path variable — order's database ID
     * @param request     validated update data
     * @param currentUser authenticated caller
     * @return updated order DTO
     */
    @PutMapping("/{id}") // PUT /api/orders/{id}
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')") // All roles may attempt; service enforces ownership
    @Operation(summary = "Update an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order updated",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "USER updating another user's order", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public ResponseEntity<OrderResponse> updateOrder(
            @Parameter(description = "Database ID of the order to update") @PathVariable Long id, // Path variable
            @Valid @RequestBody OrderRequest request, // Validated update payload
            @AuthenticationPrincipal UserDetails currentUser // Authenticated caller
    ) {
        return ResponseEntity.ok(orderService.updateOrder(id, request, currentUser.getUsername())); // 200 + body
    }

    // -------------------------------------------------------
    // DELETE /api/orders/{id}
    // -------------------------------------------------------

    /**
     * Deletes an order by ID.
     * Restricted to ADMIN and MANAGER roles.
     *
     * @param id          path variable — order's database ID
     * @param currentUser authenticated caller (must be ADMIN or MANAGER)
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}") // DELETE /api/orders/{id}
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')") // Only MANAGER and ADMIN can delete orders
    @Operation(summary = "Delete an order", description = "ADMIN and MANAGER only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Order deleted"),
            @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "Database ID of the order to delete") @PathVariable Long id, // Path variable
            @AuthenticationPrincipal UserDetails currentUser // Must be ADMIN or MANAGER
    ) {
        orderService.deleteOrder(id, currentUser.getUsername()); // Delegate to service
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
