package com.academy.paybridge.customer.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public static Customer register(String fullName, String email, String apiKeyHash, Instant now) {
        Customer c = new Customer();
        c.fullName = fullName;
        c.email = email;
        c.apiKeyHash = apiKeyHash;
        c.status = CustomerStatus.ACTIVE;
        c.createdAt = now;
        return c;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public CustomerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}