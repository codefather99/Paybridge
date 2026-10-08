package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.api.CurrentCustomer;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.RegisteredCustomer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerApi customerApi;

    public CustomerController(CustomerApi customerApi) {
        this.customerApi = customerApi;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register and receive your API key",
            description = "The key is shown ONCE. Copy it, then use the Authorize button to send it as X-API-Key.")
    public RegisterCustomerResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
        RegisteredCustomer registered = customerApi.register(request.fullName(), request.email());
        return new RegisterCustomerResponse(
                registered.customerId(), registered.fullName(), registered.apiKey(),
                "Store this key now. It cannot be shown again.");
    }

    @GetMapping("/me")
    @Operation(summary = "Who am I? Useful for checking that your API key works")
    public java.util.Map<String, Object> me() {
        return java.util.Map.of("customerId", CurrentCustomer.id());
    }
}