# Database Setup Guide

This document covers everything needed to set up MySQL 8 for the
**JWT Auth E-Commerce Backend**, from installation through table creation,
seed data, and notes on what is required at each stage of the application
lifecycle.

---

## Tech Stack

| Component      | Version  |
|----------------|----------|
| MySQL Server   | 8.x      |
| MySQL Driver   | `com.mysql:mysql-connector-j` (managed by Spring Boot) |
| ORM            | Hibernate 6.x (via Spring Data JPA)                    |
| Schema mode    | `ddl-auto: update` — Hibernate auto-creates / updates tables on startup |

---

## 1. Prerequisites (needed BEFORE starting the app)

### 1.1 Install MySQL 8

**macOS (Homebrew)**
```bash
brew install mysql        # Install MySQL 8
brew services start mysql # Start the MySQL background service
```

**Ubuntu / Debian**
```bash
sudo apt update
sudo apt install mysql-server   # Install MySQL 8
sudo systemctl start mysql      # Start the service
sudo systemctl enable mysql     # Auto-start on reboot
```

**Windows**
Download and run the MySQL 8 installer from https://dev.mysql.com/downloads/installer/
Select "Server only" or "Developer Default", then start the MySQL80 Windows Service.

---

### 1.2 Secure the root account (first-time setup only)

```bash
# Run the interactive security wizard that ships with MySQL 8
sudo mysql_secure_installation
# Follow the prompts:
#   - Set a strong root password
#   - Remove anonymous users          → Yes
#   - Disallow root login remotely    → Yes
#   - Remove test database            → Yes
#   - Reload privilege tables         → Yes
```

---

### 1.3 Create the application database and user

Connect to MySQL as root:
```bash
mysql -u root -p   # Enter the root password set in step 1.2
```

Then run the following SQL:

```sql
-- ============================================================
-- Step 1: Create the application database
-- ============================================================

CREATE DATABASE IF NOT EXISTS jwt_auth_db
    CHARACTER SET utf8mb4          -- Full Unicode support (emojis, CJK, etc.)
    COLLATE utf8mb4_unicode_ci;    -- Case-insensitive, accent-insensitive collation


-- ============================================================
-- Step 2: Create a dedicated application user
--         Never connect with root from application code.
-- ============================================================

CREATE USER IF NOT EXISTS 'jwt_auth_user'@'localhost'
    IDENTIFIED BY 'StrongP@ssw0rd!';   -- Replace with a strong password in production


-- ============================================================
-- Step 3: Grant only the permissions the app actually needs
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE,   -- DML: read and write data
      CREATE, ALTER, INDEX,             -- DDL: Hibernate ddl-auto=update needs these at startup
      DROP,                             -- DDL: Hibernate may drop/recreate columns on schema changes
      REFERENCES                        -- DDL: required to create foreign key constraints
    ON jwt_auth_db.*
    TO 'jwt_auth_user'@'localhost';

-- Apply the privilege changes immediately
FLUSH PRIVILEGES;


-- ============================================================
-- Step 4: Verify the setup
-- ============================================================

SHOW DATABASES;                          -- jwt_auth_db should appear in the list
SHOW GRANTS FOR 'jwt_auth_user'@'localhost';   -- Confirm the grants above are present
```

> **Production note:** Once the schema is stable, remove `CREATE`, `ALTER`, `DROP`, and `INDEX`
> grants and switch `ddl-auto` to `validate` in `application.yaml`. The app only needs
> `SELECT`, `INSERT`, `UPDATE`, and `DELETE` at runtime.

---

### 1.4 Configure environment variables

The app reads credentials from environment variables (see `application.yaml`).
Set these **before** starting the application:

```bash
# Database credentials (must match what you created in step 1.3)
export DB_USERNAME=jwt_auth_user
export DB_PASSWORD=StrongP@ssw0rd!

# JWT signing key — 256-bit value encoded as a hex string
# Generate a new one with: openssl rand -hex 32
export JWT_SECRET_KEY=your_256bit_hex_key_here

# Optional: override the default server port (8081)
export SERVER_PORT=8081
```

**Windows (Command Prompt)**
```cmd
set DB_USERNAME=jwt_auth_user
set DB_PASSWORD=StrongP@ssw0rd!
set JWT_SECRET_KEY=your_256bit_hex_key_here
```

**Windows (PowerShell)**
```powershell
$env:DB_USERNAME  = "jwt_auth_user"
$env:DB_PASSWORD  = "StrongP@ssw0rd!"
$env:JWT_SECRET_KEY = "your_256bit_hex_key_here"
```

---

## 2. What Hibernate creates automatically at startup

With `spring.jpa.hibernate.ddl-auto: update` set in `application.yaml`,
Hibernate inspects the entity classes on every startup and creates or alters
tables to match. **You do not need to run CREATE TABLE statements manually.**

The three tables created are:

### 2.1 `users`

Maps to `com.my.jwt.entity.User`.

```sql
-- Hibernate generates this DDL equivalent:
CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,  -- Primary key
    first_name  VARCHAR(255)    NOT NULL,                 -- User's first name
    last_name   VARCHAR(255)    NOT NULL,                 -- User's last name
    email       VARCHAR(255)    NOT NULL UNIQUE,          -- Login identifier; must be unique
    password    VARCHAR(255)    NOT NULL,                 -- BCrypt hash (never plain text)
    role        VARCHAR(50)     NOT NULL,                 -- Enum: ADMIN | MANAGER | USER
    PRIMARY KEY (id)
);
```

### 2.2 `refresh_tokens`

Maps to `com.my.jwt.entity.RefreshToken`.

```sql
-- Hibernate generates this DDL equivalent:
CREATE TABLE refresh_tokens (
    id          BIGINT          NOT NULL AUTO_INCREMENT,  -- Primary key
    token       VARCHAR(512)    NOT NULL UNIQUE,          -- Opaque UUID token string
    user_id     BIGINT          NOT NULL,                 -- FK → users.id
    expires_at  DATETIME(6)     NOT NULL,                 -- UTC expiry timestamp
    revoked     TINYINT(1)      NOT NULL DEFAULT 0,       -- 0 = active, 1 = revoked
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

### 2.3 `orders`

Maps to `com.my.jwt.entity.Order`.

```sql
-- Hibernate generates this DDL equivalent:
CREATE TABLE orders (
    id            BIGINT          NOT NULL AUTO_INCREMENT,  -- Primary key
    user_id       BIGINT          NOT NULL,                 -- FK → users.id (order owner)
    description   VARCHAR(255)    NOT NULL,                 -- Human-readable order summary
    total_amount  DECIMAL(12,2)   NOT NULL,                 -- Monetary value
    status        VARCHAR(50)     NOT NULL,                 -- Enum: PENDING | PROCESSING | SHIPPED | DELIVERED | CANCELLED
    created_at    DATETIME(6)     NOT NULL,                 -- Set by @PrePersist; never updated
    updated_at    DATETIME(6)     NOT NULL,                 -- Refreshed by @PreUpdate on every save
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

---

## 3. Optional: seed an ADMIN user (needed at runtime for admin operations)

Hibernate does **not** insert any rows. To have an ADMIN or MANAGER user
available immediately after first startup, insert one manually after the
tables are created.

> **Important:** The `password` column must contain a **BCrypt hash**, never plain text.
> Use an online BCrypt generator or the snippet below.

**Generate a BCrypt hash (Java one-liner):**
```java
// Run once in a scratch file or unit test:
System.out.println(new BCryptPasswordEncoder().encode("AdminP@ss123!"));
// Copy the output — it looks like: $2a$10$...
```

**Insert the seed admin user:**
```sql
-- Connect to the database first
USE jwt_auth_db;

-- Insert an ADMIN user (replace the hash with your own BCrypt output)
INSERT INTO users (first_name, last_name, email, password, role)
VALUES (
    'System',                                                          -- first_name
    'Admin',                                                           -- last_name
    'admin@jwt-auth-app.com',                                          -- email (login)
    '$2a$10$XURPShQNCsLjp1ESc2laoObo9QZDhxz73hJPaEv7/cVham4eOykna',  -- BCrypt hash of "AdminP@ss123!"
    'ADMIN'                                                            -- role
);

-- Verify the insert
SELECT id, email, role FROM users;
```

---

## 4. Startup checklist

Run through this list every time before starting the application:

- [ ] MySQL 8 service is running (`systemctl status mysql` / check Services on Windows)
- [ ] Database `jwt_auth_db` exists (`SHOW DATABASES;`)
- [ ] User `jwt_auth_user` exists and has the correct grants (`SHOW GRANTS FOR 'jwt_auth_user'@'localhost';`)
- [ ] Environment variables are set: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`
- [ ] No other process is using port `8081` (or whichever `SERVER_PORT` is set to)

Start the app:
```bash
./mvnw spring-boot:run
# or
java -jar target/JWT_Auth-1.0.0.jar
```

On first startup, Hibernate will print the DDL it is executing (visible at
`DEBUG` log level for `org.hibernate.SQL`). The three tables are created
automatically if they do not exist.

---

## 5. Runtime database activity

Once the application is running, the following SQL activity occurs per
endpoint call:

| Endpoint | Tables touched | Operation |
|---|---|---|
| `POST /api/v1/auth/register` | `users`, `refresh_tokens` | INSERT user, INSERT refresh token |
| `POST /api/v1/auth/login` | `users`, `refresh_tokens` | SELECT user, DELETE old tokens, INSERT new token |
| `POST /api/v1/auth/refresh` | `refresh_tokens` | SELECT token, DELETE old, INSERT new |
| `POST /api/v1/auth/logout` | `refresh_tokens` | SELECT token, DELETE all user tokens |
| `GET /api/v1/orders` | `orders`, `users` | SELECT orders (filtered by user for USER role) |
| `GET /api/v1/orders/{id}` | `orders`, `users` | SELECT single order |
| `POST /api/v1/orders` | `orders` | INSERT order |
| `PUT /api/v1/orders/{id}` | `orders` | UPDATE order |
| `DELETE /api/v1/orders/{id}` | `orders` | DELETE order |
| Every authenticated request | `users` | SELECT user by email (JWT filter loads UserDetails) |

---

## 6. Useful diagnostic queries

Run these while the app is running to inspect live state:

```sql
-- View all registered users (never SELECT password in production logs)
SELECT id, first_name, last_name, email, role FROM users;

-- View all active (non-expired, non-revoked) refresh tokens
SELECT rt.id, u.email, rt.expires_at, rt.revoked
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
WHERE rt.revoked = 0 AND rt.expires_at > NOW();

-- View all orders with owner email
SELECT o.id, u.email AS owner, o.description, o.total_amount, o.status, o.created_at
FROM orders o
JOIN users u ON o.user_id = u.id
ORDER BY o.created_at DESC;

-- Count orders per user
SELECT u.email, COUNT(o.id) AS total_orders
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
GROUP BY u.email;
```

---

## 7. Production hardening (before going live)

```yaml
# In application.yaml — switch from update to validate once schema is stable
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # Hibernate checks schema matches entities but makes NO changes
```

```sql
-- Revoke DDL permissions from the app user in production
REVOKE CREATE, ALTER, DROP, INDEX ON jwt_auth_db.* FROM 'jwt_auth_user'@'localhost';
FLUSH PRIVILEGES;
-- App now only has SELECT, INSERT, UPDATE, DELETE — principle of least privilege
```
