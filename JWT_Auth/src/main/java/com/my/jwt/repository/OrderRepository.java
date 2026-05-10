package com.my.jwt.repository;

import com.my.jwt.entity.Order; // Order entity
import com.my.jwt.entity.User; // User entity
import org.springframework.data.jpa.repository.JpaRepository; // Base CRUD repository
import org.springframework.stereotype.Repository; // Spring Data marker

import java.util.List; // Result of multi-row queries

/**
 * Spring Data JPA repository for {@link Order} entities.
 */
@Repository // Enables Spring Data proxy creation
public interface OrderRepository extends JpaRepository<Order, Long> { // Long = PK type

    /**
     * Returns all orders placed by a specific user.
     * Used when the authenticated user has the USER role.
     *
     * @param user the owner of the orders
     * @return list of orders belonging to this user
     */
    List<Order> findByUser(User user); // SELECT * FROM orders WHERE user_id = ?
}
