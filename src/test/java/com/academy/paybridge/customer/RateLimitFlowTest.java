package com.academy.paybridge.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "paybridge.rate-limit.enabled=true")
@ActiveProfiles("test")
class RateLimitFlowTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void registrationIsLimitedPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(register().statusCode()).isEqualTo(201);
        }

        HttpResponse<String> sixth = register();

        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(sixth.headers().firstValue("Retry-After")).isPresent();
        assertThat(sixth.body()).contains("RATE_LIMITED");
    }

    private HttpResponse<String> register() throws Exception {
        String json = """
                {"fullName":"Test User","email":"user-%s@example.com"}
                """.formatted(UUID.randomUUID());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/customers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}