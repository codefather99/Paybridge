package com.academy.paybridge.customer.api;

import java.util.Optional;

public interface CustomerApi {

    RegisteredCustomer register(String fullName, String email);

    /** Empty if the key is unknown or the customer is suspended. */
    Optional<CustomerView> authenticate(String apiKey);
}