# Project instructions
This is a very simple yet production grade 
Spring Boot application with end to end JWT
authentication and authorization implemented
with the below-mentioned tech stack. 
Always generate production-grade code.
This will be a simple e-commerce backend, 
where a customer can order products.

In this project first of all we will create
the security endpoints like:
- /api/v1/auth/register
- /api/v1/auth/login
- /api/v1/auth/refresh (for refresh token)
- /api/v1/auth/logout

For roles, we will have 3 roles: 
- ADMIN (Administrator with full system access)
- MANAGER (Manager with operational access to orders and products)
- USER (Regular user with read-only access)

Security considerations:
- The JWT token will expire in 10 minutes
- The JWT token will be included in the response body and the refresh token in the HttpOnly cookie
- The JWT refresh token will have 7 days expiry
- Security:
  - Refresh token in HttpOnly cookie: Cannot be accessed by JavaScript (XSS protection)
  - Refresh token rotation: Old refresh token becomes invalid after use
  - Refresh token stored in DB: Can be revoked on logout
- Refresh token expiry: 7 days (must login again after that)
- Flow:
  1. Client's access token expires (after 10 minutes)
  2. Client sends POST to this endpoint (browser automatically includes cookie)
  3. Server extracts refresh token from cookie
  4. Server validates refresh token
  5. Server issues new access token + new refresh token
  6. New refresh token set as HttpOnly cookie
  7. Client continues using API with new access token
- Do not store passwords in plain texts
- Use the application.yaml file for all the dynamic configs
- Use code comments for each and every line of code even for pom.xml or .yaml files

## Backend Tech Stack
- Use Apache Maven 3.9.14
- Use Java 25 (java 25.0.2 2026-01-20 LTS)
- Use Spring Boot 4.0.6
- Use Spring Cloud 2025.1.1
- Use MySQL 8 database
- Use Lombok
- Use JWT authentication and authorization
- Use REST endpoints

## REST endpoints
| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| GET | `/api/orders` | List all orders | Yes |
| GET | `/api/orders/{id}` | Fetch specific order | Yes |
| POST | `/api/orders` | Create a new order | Yes |
| PUT | `/api/orders/{id}` | Update order | Yes |
| DELETE | `/api/orders/{id}` | Remove an order | Yes |
| POST | `/api/auth/login` | Authenticate user and generate JWT | No |
| POST | `/api/auth/signup` | Register new users | No |

Note: for the GET `/api/orders` endpoint, it
will first check the role. If the role is 
USER, then only the orders that user will
be returned. Or else if the role is MANAGER or ADMIN
then all the orders will be returned.


