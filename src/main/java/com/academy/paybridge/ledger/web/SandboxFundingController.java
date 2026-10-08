package com.academy.paybridge.ledger.web;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountView;
import com.academy.paybridge.account.api.SystemAccounts;
import com.academy.paybridge.customer.api.CurrentCustomer;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.ledger.api.PostingResult;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.idempotency.IdempotencyKeys;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sandbox")
@Tag(name = "Sandbox")
@ConditionalOnProperty(prefix = "paybridge.sandbox", name = "funding-enabled", havingValue = "true")
public class SandboxFundingController {

    private final AccountApi accountApi;
    private final LedgerApi ledgerApi;

    public SandboxFundingController(AccountApi accountApi, LedgerApi ledgerApi) {
        this.accountApi = accountApi;
        this.ledgerApi = ledgerApi;
    }

    @PostMapping("/fund")
    @Operation(summary = "Add test money to one of my accounts (sandbox only)")
    public FundingResponse fund(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                @Valid @RequestBody FundAccountRequest request) {
        UUID caller = CurrentCustomer.id();
        String scopedKey = IdempotencyKeys.scoped(caller, idempotencyKey);

        AccountView target = accountApi.requireOwned(request.accountNumber(), caller);
        if (target.currency() != Currency.NGN) {
            throw new BusinessException("UNSUPPORTED_CURRENCY",
                    "Sandbox funding is only available for NGN accounts");
        }

        Money amount = Money.ofMajor(request.amount(), target.currency());
        PostingRequest posting = new PostingRequest(
                "FUND:" + scopedKey,
                LedgerTransactionType.FUNDING,
                "Sandbox funding of " + target.accountNumber(),
                List.of(
                        PostingLine.debit(SystemAccounts.SANDBOX_FUNDING_NGN, amount),
                        PostingLine.credit(target.id(), amount)));

        PostingResult result = ledgerApi.post(posting);
        AccountView updated = accountApi.requireOwned(request.accountNumber(), caller);

        return new FundingResponse(
                result.transactionId(),
                result.reference(),
                result.replayed(),
                updated.accountNumber(),
                updated.balance().toMajor().toPlainString());
    }
}