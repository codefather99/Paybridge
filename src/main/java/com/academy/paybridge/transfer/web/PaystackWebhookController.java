package com.academy.paybridge.transfer.web;

import com.academy.paybridge.transfer.client.paystack.PaystackWebhookVerifier;
import com.academy.paybridge.transfer.service.PaystackWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden   // called by Paystack, not by people; Swagger cannot produce a valid signature
@RestController
@RequestMapping("/api/v1/webhooks")
public class PaystackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookController.class);

    private final PaystackWebhookVerifier verifier;
    private final PaystackWebhookService webhookService;

    public PaystackWebhookController(PaystackWebhookVerifier verifier, PaystackWebhookService webhookService) {
        this.verifier = verifier;
        this.webhookService = webhookService;
    }

    @PostMapping("/paystack")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (!verifier.isAuthentic(rawBody, signature)) {
            log.warn("Rejected a webhook with a missing or invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.process(rawBody);
        return ResponseEntity.ok().build();
    }
}