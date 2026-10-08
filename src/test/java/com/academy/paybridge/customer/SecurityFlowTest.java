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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityFlowTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Response(int status, String body) {
    }

    // ---- authentication ----

    @Test
    void requestsWithoutAKeyAreRejected() throws Exception {
        Response response = call("GET", "/api/v1/accounts", null, null, null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
    }

    @Test
    void aWrongKeyIsRejected() throws Exception {
        Response response = call("GET", "/api/v1/accounts", "pbk_not-a-real-key", null, null);

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void theApiDocumentationIsPublic() throws Exception {
        assertThat(call("GET", "/v3/api-docs", null, null, null).status()).isEqualTo(200);
    }

    @Test
    void registeringTwiceWithTheSameEmailIsRefused() throws Exception {
        String email = uniqueEmail();
        assertThat(register(email).status()).isEqualTo(201);

        assertThat(register(email).status()).isEqualTo(409);
    }

    // ---- ownership ----

    @Test
    void aCustomerCanOpenAndListTheirOwnAccounts() throws Exception {
        String key = apiKeyOf(register(uniqueEmail()));
        String number = openAccount(key);

        Response list = call("GET", "/api/v1/accounts", key, null, null);

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(number);
    }

    @Test
    void aCustomerCannotSeeAnotherCustomersAccount() throws Exception {
        String adaKey = apiKeyOf(register(uniqueEmail()));
        String bolaKey = apiKeyOf(register(uniqueEmail()));
        String adaAccount = openAccount(adaKey);

        Response response = call("GET", "/api/v1/accounts/" + adaAccount, bolaKey, null, null);

        assertThat(response.status()).isEqualTo(404);   // not 403: do not confirm the number exists
    }

    @Test
    void aCustomerCannotSpendFromAnotherCustomersAccount() throws Exception {
        String adaKey = apiKeyOf(register(uniqueEmail()));
        String bolaKey = apiKeyOf(register(uniqueEmail()));
        String adaAccount = openAccount(adaKey);
        String bolaAccount = openAccount(bolaKey);
        assertThat(fund(adaKey, adaAccount, "fund-1", "5000.00").status()).isEqualTo(200);

        // Bola tries to move Ada's money into his own account.
        String steal = """
                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":4000.00}
                """.formatted(adaAccount, bolaAccount);
        Response response = call("POST", "/api/v1/transfers", bolaKey, "steal-1", steal);

        assertThat(response.status()).isEqualTo(404);
        Response adaAfter = call("GET", "/api/v1/accounts/" + adaAccount, adaKey, null, null);
        assertThat(adaAfter.body()).contains("\"balance\":\"5000.00\"");
    }

    @Test
    void theSameIdempotencyKeyFromTwoCustomersDoesNotCollide() throws Exception {
        String adaKey = apiKeyOf(register(uniqueEmail()));
        String bolaKey = apiKeyOf(register(uniqueEmail()));
        String adaAccount = openAccount(adaKey);
        String bolaAccount = openAccount(bolaKey);

        Response ada = fund(adaKey, adaAccount, "same-key", "100.00");
        Response bola = fund(bolaKey, bolaAccount, "same-key", "100.00");

        assertThat(ada.body()).contains("\"replayed\":false");
        assertThat(bola.body()).contains("\"replayed\":false");   // not treated as a replay of Ada's
    }

    // ---- helpers ----

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private Response register(String email) throws Exception {
        String json = """
                {"fullName":"Test User","email":"%s"}
                """.formatted(email);
        return call("POST", "/api/v1/customers/register", null, null, json);
    }

    private String apiKeyOf(Response registration) {
        assertThat(registration.status()).isEqualTo(201);
        return field(registration.body(), "apiKey");
    }

    private String openAccount(String apiKey) throws Exception {
        Response response = call("POST", "/api/v1/accounts", apiKey, null, "{\"currency\":\"NGN\"}");
        assertThat(response.status()).isEqualTo(201);
        return field(response.body(), "accountNumber");
    }

    private Response fund(String apiKey, String accountNumber, String idempotencyKey, String amount) throws Exception {
        String json = """
                {"accountNumber":"%s","amount":%s}
                """.formatted(accountNumber, amount);
        return call("POST", "/api/v1/sandbox/fund", apiKey, idempotencyKey, json);
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", name, json).isTrue();
        return matcher.group(1);
    }

    private Response call(String method, String path, String apiKey, String idempotencyKey, String json)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        builder.method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));

        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}