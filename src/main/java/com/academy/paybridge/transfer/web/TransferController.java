package com.academy.paybridge.transfer.web;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.customer.api.CurrentCustomer;
import com.academy.paybridge.shared.idempotency.IdempotencyKeys;
import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.ExternalTransferCommand;
import com.academy.paybridge.transfer.api.TransferApi;
import com.academy.paybridge.transfer.api.TransferCommand;
import com.academy.paybridge.transfer.api.TransferResult;
import com.academy.paybridge.transfer.api.TransferView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private final TransferApi transferApi;
    private final ExternalTransferApi externalTransferApi;
    private final AccountApi accountApi;

    public TransferController(TransferApi transferApi,
                              ExternalTransferApi externalTransferApi,
                              AccountApi accountApi) {
        this.transferApi = transferApi;
        this.externalTransferApi = externalTransferApi;
        this.accountApi = accountApi;
    }

    @PostMapping
    @Operation(summary = "Transfer money from one of my accounts to any PayBridge account",
            description = "Send the same Idempotency-Key when retrying. A replay returns 200 with replayed=true.")
    public ResponseEntity<TransferResponse> transfer(
            @Parameter(description = "Unique string for this intended transfer, e.g. trf-001", example = "trf-001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID caller = CurrentCustomer.id();
        accountApi.requireOwned(request.sourceAccountNumber(), caller);   // the source must be MINE

        TransferResult result = transferApi.transfer(new TransferCommand(
                IdempotencyKeys.scoped(caller, idempotencyKey),
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amount(),
                request.narration()));

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransferResponse.from(result));
    }

    @PostMapping("/external")
    @Operation(summary = "Send money from one of my accounts to any Nigerian bank account (via Paystack)",
            description = "Returns 202 while the payout is PROCESSING and 201 once it is final. "
                    + "The 'status' field in the body is the real state of the payment. "
                    + "Use POST /{id}/refresh to ask the provider about a PROCESSING transfer.")
    public ResponseEntity<TransferResponse> external(
            @Parameter(description = "Unique string for this intended transfer", example = "ext-001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExternalTransferRequest request) {

        UUID caller = CurrentCustomer.id();
        accountApi.requireOwned(request.sourceAccountNumber(), caller);

        TransferResult result = externalTransferApi.transferExternal(new ExternalTransferCommand(
                IdempotencyKeys.scoped(caller, idempotencyKey),
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.destinationBankCode(),
                request.amount(),
                request.narration()));

        HttpStatus status;
        if (result.replayed()) {
            status = HttpStatus.OK;
        } else if ("PROCESSING".equals(result.transfer().status())) {
            status = HttpStatus.ACCEPTED;
        } else {
            status = HttpStatus.CREATED;
        }
        return ResponseEntity.status(status).body(TransferResponse.from(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my transfers and its current status")
    public TransferResponse get(@PathVariable UUID id) {
        TransferView view = transferApi.getTransfer(id);
        accountApi.requireOwned(view.sourceAccountNumber(), CurrentCustomer.id());
        return TransferResponse.from(view, false);
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "Ask the provider about one of my PROCESSING external transfers and settle it",
            description = "Safe to call repeatedly. Does nothing if the transfer is already final.")
    public TransferResponse refresh(@PathVariable UUID id) {
        TransferView view = transferApi.getTransfer(id);
        accountApi.requireOwned(view.sourceAccountNumber(), CurrentCustomer.id());
        return TransferResponse.from(externalTransferApi.refresh(id), false);
    }
}