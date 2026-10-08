package com.academy.paybridge.transfer;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountView;
import com.academy.paybridge.account.api.SystemAccounts;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.FakeTransferGateway.Mode;
import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.ExternalTransferCommand;
import com.academy.paybridge.transfer.api.TransferApi;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.service.PaystackWebhookService;
import com.academy.paybridge.transfer.service.PaystackWebhookService.Result;
import com.academy.paybridge.transfer.service.StuckTransferReconciler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeGatewayConfig.class)
class WebhookAndReconcilerTest {

    @Autowired AccountApi accountApi;
    @Autowired TransferApi transferApi;
    @Autowired ExternalTransferApi externalTransferApi;
    @Autowired LedgerApi ledgerApi;
    @Autowired FakeTransferGateway fake;
    @Autowired StuckTransferReconciler reconciler;
    @Autowired PaystackWebhookService webhookService;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    // ---- reconciliation job ----

    @Test
    void reconcilerCompletesATransferThatTheProviderActuallyPaid() {
        AccountView ada = fundedAccount(100_000);
        TransferView stuck = sendAndExpectProcessing(ada, Mode.TIMEOUT_BUT_PROCESSED);

        TransferView after = reconcileUntilSettled(stuck.id());

        assertThat(after.status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);
    }

    @Test
    void reconcilerReversesAPaymentTheProviderNeverReceived() {
        AccountView ada = fundedAccount(100_000);
        TransferView stuck = sendAndExpectProcessing(ada, Mode.TIMEOUT_NOT_PROCESSED);

        TransferView after = reconcileUntilSettled(stuck.id());

        assertThat(after.status()).isEqualTo("REVERSED");
        assertThat(balanceOf(ada)).isEqualTo(100_000);
    }

    // ---- webhooks ----

    @Test
    void successWebhookCompletesAProcessingTransfer() {
        AccountView ada = fundedAccount(100_000);
        TransferView pending = sendAndExpectProcessing(ada, Mode.ACCEPT_PENDING);

        Result result = webhookService.process(body("transfer.success", pending, 30_000));

        assertThat(result).isEqualTo(Result.PROCESSED);
        assertThat(transferApi.getTransfer(pending.id()).status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);
    }

    @Test
    void failedWebhookReversesAndRefundsTheCustomer() {
        AccountView ada = fundedAccount(100_000);
        TransferView pending = sendAndExpectProcessing(ada, Mode.ACCEPT_PENDING);

        Result result = webhookService.process(body("transfer.failed", pending, 30_000));

        assertThat(result).isEqualTo(Result.PROCESSED);
        assertThat(transferApi.getTransfer(pending.id()).status()).isEqualTo("REVERSED");
        assertThat(balanceOf(ada)).isEqualTo(100_000);
    }

    @Test
    void duplicateWebhookDeliveryIsHarmless() {
        AccountView ada = fundedAccount(100_000);
        TransferView pending = sendAndExpectProcessing(ada, Mode.ACCEPT_PENDING);

        webhookService.process(body("transfer.success", pending, 30_000));
        webhookService.process(body("transfer.success", pending, 30_000));

        assertThat(transferApi.getTransfer(pending.id()).status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);
    }

    @Test
    void aLateFailureAfterCompletionIsNotAutomaticallyRefunded() {
        AccountView ada = fundedAccount(100_000);
        TransferView pending = sendAndExpectProcessing(ada, Mode.ACCEPT_PENDING);
        webhookService.process(body("transfer.success", pending, 30_000));

        webhookService.process(body("transfer.failed", pending, 30_000));

        assertThat(transferApi.getTransfer(pending.id()).status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);   // no refund without a human looking
    }

    @Test
    void aWebhookWithTheWrongAmountIsRefusedAndChangesNothing() {
        AccountView ada = fundedAccount(100_000);
        TransferView pending = sendAndExpectProcessing(ada, Mode.ACCEPT_PENDING);

        Result result = webhookService.process(body("transfer.success", pending, 1));

        assertThat(result).isEqualTo(Result.AMOUNT_MISMATCH);
        assertThat(transferApi.getTransfer(pending.id()).status()).isEqualTo("PROCESSING");
    }

    @Test
    void aWebhookForAnUnknownReferenceIsIgnored() {
        byte[] body = raw("transfer.success", "trf_" + UUID.randomUUID(), 30_000);

        assertThat(webhookService.process(body)).isEqualTo(Result.UNKNOWN_REFERENCE);
    }

    @Test
    void unrelatedEventTypesAreIgnored() {
        byte[] body = raw("charge.success", "some-reference", 30_000);

        assertThat(webhookService.process(body)).isEqualTo(Result.IGNORED_EVENT);
    }

    @Test
    void garbageThatSomehowCarriesAValidSignatureIsReportedAsMalformed() {
        byte[] body = "this is not json".getBytes(StandardCharsets.UTF_8);

        assertThat(webhookService.process(body)).isEqualTo(Result.MALFORMED);
    }

    // ---- helpers ----

    private TransferView sendAndExpectProcessing(AccountView from, Mode mode) {
        fake.setMode(mode);
        TransferView view = externalTransferApi.transferExternal(new ExternalTransferCommand(
                "wh-" + UUID.randomUUID(), from.accountNumber(), "0123456789", "058",
                new BigDecimal("300.00"), "test payout")).transfer();
        assertThat(view.status()).isEqualTo("PROCESSING");
        return view;
    }

    private TransferView reconcileUntilSettled(UUID transferId) {
        // Other tests may have left PROCESSING transfers behind; loop until ours is reached.
        for (int i = 0; i < 10; i++) {
            reconciler.reconcileOnce();
            TransferView view = transferApi.getTransfer(transferId);
            if (!"PROCESSING".equals(view.status())) {
                return view;
            }
        }
        return transferApi.getTransfer(transferId);
    }

    private static byte[] body(String event, TransferView transfer, long amountKobo) {
        return raw(event, "trf_" + transfer.id(), amountKobo);
    }

    private static byte[] raw(String event, String reference, long amountKobo) {
        return """
                {"event":"%s","data":{"reference":"%s","amount":%d,"currency":"NGN","status":"x","transfer_code":"TRF_test"}}
                """.formatted(event, reference, amountKobo).getBytes(StandardCharsets.UTF_8);
    }

    private AccountView fundedAccount(long kobo) {
        AccountView account = accountApi.openAccount(UUID.randomUUID(), Currency.NGN);
        Money amount = new Money(kobo, Currency.NGN);
        ledgerApi.post(new PostingRequest(
                "TEST-FUND:" + UUID.randomUUID(),
                LedgerTransactionType.FUNDING,
                "test funding",
                List.of(PostingLine.debit(SystemAccounts.SANDBOX_FUNDING_NGN, amount),
                        PostingLine.credit(account.id(), amount))));
        return account;
    }

    private long balanceOf(AccountView account) {
        return accountApi.getByAccountNumber(account.accountNumber()).balance().minorUnits();
    }
}