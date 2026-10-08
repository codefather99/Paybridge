package com.academy.paybridge.transfer.service;

import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;
import com.academy.paybridge.transfer.client.paystack.PaystackWebhookEvent;
import com.academy.paybridge.transfer.domain.WebhookEvent;
import com.academy.paybridge.transfer.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;

@Service
public class PaystackWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookService.class);

    public enum Result { PROCESSED, IGNORED_EVENT, UNKNOWN_REFERENCE, AMOUNT_MISMATCH, MALFORMED }

    private final ExternalTransferApi externalTransferApi;
    private final WebhookEventRepository eventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public PaystackWebhookService(ExternalTransferApi externalTransferApi,
                                  WebhookEventRepository eventRepository,
                                  JsonMapper jsonMapper,
                                  Clock clock) {
        this.externalTransferApi = externalTransferApi;
        this.eventRepository = eventRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Call only after the signature has been verified. */
    public Result process(byte[] rawBody) {
        String payload = new String(rawBody, StandardCharsets.UTF_8);

        PaystackWebhookEvent event = null;
        try {
            event = jsonMapper.readValue(rawBody, PaystackWebhookEvent.class);
        } catch (RuntimeException e) {
            log.error("Received a correctly signed webhook that could not be parsed");
        }

        String eventType = event == null ? null : event.event();
        String reference = event == null || event.data() == null ? null : event.data().reference();

        // Record first, act second: if processing crashes, the raw event is still on file.
        WebhookEvent audit = eventRepository.save(
                WebhookEvent.received("PAYSTACK", eventType, reference, payload, clock.instant()));

        Result result = dispatch(event);

        audit.finish(result.name(), clock.instant());
        eventRepository.save(audit);
        return result;
    }

    private Result dispatch(PaystackWebhookEvent event) {
        if (event == null || event.event() == null) {
            return Result.MALFORMED;
        }

        GatewayTransferStatus status = switch (event.event()) {
            case "transfer.success" -> GatewayTransferStatus.SUCCESS;
            case "transfer.failed" -> GatewayTransferStatus.FAILED;
            case "transfer.reversed" -> GatewayTransferStatus.REVERSED;
            default -> null;
        };
        if (status == null) {
            return Result.IGNORED_EVENT;
        }
        if (event.data() == null || event.data().reference() == null) {
            return Result.MALFORMED;
        }

        try {
            Optional<TransferView> updated = externalTransferApi.applyProviderUpdate(
                    event.data().reference(), status, event.data().transferCode(), event.data().amount());
            if (updated.isEmpty()) {
                // Normal for transfers made outside this app. Retrying would never help, so we say 200.
                log.warn("Webhook {} for unknown reference {}", event.event(), event.data().reference());
                return Result.UNKNOWN_REFERENCE;
            }
            return Result.PROCESSED;
        } catch (BusinessException e) {
            if ("AMOUNT_MISMATCH".equals(e.getCode())) {
                log.error("Webhook refused: {}", e.getMessage());
                return Result.AMOUNT_MISMATCH;
            }
            throw e;
        }
    }
}