package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.api.AuthenticatedCustomer;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.CustomerView;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads X-API-Key, looks the customer up, and records who is calling.
 * Deliberately NOT a @Component: a filter bean would also be registered by Spring Boot
 * outside the security chain. SecurityConfig creates the one instance we want.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private final CustomerApi customerApi;
    private final AuthenticationEntryPoint entryPoint;

    ApiKeyAuthenticationFilter(CustomerApi customerApi, AuthenticationEntryPoint entryPoint) {
        this.customerApi = customerApi;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            Optional<CustomerView> customer = customerApi.authenticate(apiKey.trim());
            if (customer.isEmpty()) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
                return;
            }

            AuthenticatedCustomer principal =
                    new AuthenticatedCustomer(customer.get().id(), customer.get().fullName());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        chain.doFilter(request, response);
    }
}