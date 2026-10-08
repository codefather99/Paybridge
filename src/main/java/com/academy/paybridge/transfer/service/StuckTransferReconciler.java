package com.academy.paybridge.transfer.service;

import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.domain.Transfer;
import com.academy.paybridge.transfer.domain.TransferStatus;
import com.academy.paybridge.transfer.domain.TransferType;
import com.academy.paybridge.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Periodically asks the provider about transfers that have been PROCESSING for a while. */
@Component
public class StuckTransferReconciler {

    private static final Logger log = LoggerFactory.getLogger(StuckTransferReconciler.class);

    private final TransferRepository transferRepository;
    private final ExternalTransferApi externalTransferApi;
    private final Clock clock;
    private final Duration minAge;
    private final Duration alertAfter;

    public StuckTransferReconciler(TransferRepository transferRepository,
                                   ExternalTransferApi externalTransferApi,
                                   Clock clock,
                                   @Value("${paybridge.transfers.reconcile-min-age:PT30S}") Duration minAge,
                                   @Value("${paybridge.transfers.stuck-alert-after:PT1H}") Duration alertAfter) {
        this.transferRepository = transferRepository;
        this.externalTransferApi = externalTransferApi;
        this.clock = clock;
        this.minAge = minAge;
        this.alertAfter = alertAfter;
    }

    @Scheduled(fixedDelayString = "${paybridge.transfers.reconcile-interval:PT1M}",
            initialDelayString = "PT30S")
    public void run() {
        reconcileOnce();
    }

    /** Returns how many transfers left PROCESSING during this pass. */
    public int reconcileOnce() {
        Instant now = clock.instant();
        List<Transfer> stuck = transferRepository
                .findTop50ByStatusAndTypeAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        TransferStatus.PROCESSING, TransferType.EXTERNAL, now.minus(minAge));

        if (!stuck.isEmpty()) {
            log.info("Reconciling {} PROCESSING transfer(s)", stuck.size());
        }

        int settled = 0;
        for (Transfer transfer : stuck) {
            try {
                TransferView after = externalTransferApi.refresh(transfer.getId());
                if (!"PROCESSING".equals(after.status())) {
                    settled++;
                } else if (Duration.between(transfer.getUpdatedAt(), now).compareTo(alertAfter) > 0) {
                    log.error("Transfer {} has been PROCESSING for over {}. Needs manual attention.",
                            transfer.getId(), alertAfter);
                }
            } catch (RuntimeException e) {
                // One bad transfer must never stop the others from being reconciled.
                log.warn("Could not reconcile transfer {}: {}", transfer.getId(), e.getMessage());
            }
        }
        return settled;
    }
}