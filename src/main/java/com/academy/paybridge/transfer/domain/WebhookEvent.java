package com.academy.paybridge.transfer.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

    private static final int MAX_PAYLOAD = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "event_type", updatable = false, length = 60)
    private String eventType;

    @Column(updatable = false, length = 80)
    private String reference;

    @Column(nullable = false, updatable = false, length = MAX_PAYLOAD)
    private String payload;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEvent() {
    }

    public static WebhookEvent received(String provider, String eventType, String reference,
                                        String payload, Instant now) {
        WebhookEvent e = new WebhookEvent();
        e.provider = provider;
        e.eventType = eventType;
        e.reference = reference;
        e.payload = payload.length() > MAX_PAYLOAD ? payload.substring(0, MAX_PAYLOAD) : payload;
        e.result = "RECEIVED";
        e.receivedAt = now;
        return e;
    }

    public void finish(String result, Instant now) {
        this.result = result;
        this.processedAt = now;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getReference() { return reference; }
    public String getResult() { return result; }
    public Instant getReceivedAt() { return receivedAt; }
}