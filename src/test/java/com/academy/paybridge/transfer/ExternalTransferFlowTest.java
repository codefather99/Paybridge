package com.academy.paybridge.transfer;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountView;
import com.academy.paybridge.account.api.SystemAccounts;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.FakeTransferGateway.Mode;
import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.ExternalTransferCommand;
import com.academy.paybridge.transfer.api.TransferResult;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(ExternalTransferFlowTest.FakeGatewayConfig.class)
class ExternalTransferFlowTest {

    @SpringBootTest
    static class FakeGatewayConfig {
        @Bean
        @Primary
        FakeTransferGateway fakeTransferGateway() {
            return new FakeTransferGateway();
        }
    }

    private static final String CLEARING_NUMBER = "0000000002";

    @Autowired AccountApi accountApi;
    @Autowired ExternalTransferApi externalTransferApi;
    @Autowired LedgerApi ledgerApi;
    @Autowired FakeTransferGateway fake;

    @BeforeEach
    void resetFake() {
        fake.reset();
    }

    @Test
    void successCompletesAndKeepsTheMoneyInClearing() {
        AccountView ada = fundedAccount(100_000);
        long clearingBefore = balanceOfNumber(CLEARING_NUMBER);

        TransferResult result = send(ada, "success-" + UUID.randomUUID(), "300.00");

        assertThat(result.transfer().status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);
        assertThat(balanceOfNumber(CLEARING_NUMBER) - clearingBefore).isEqualTo(30_000);
    }

    @Test
    void providerRejectionReversesTheCustomerInFull() {
        AccountView ada = fundedAccount(100_000);
        long clearingBefore = balanceOfNumber(CLEARING_NUMBER);
        fake.setMode(Mode.REJECT);

        TransferResult result = send(ada, "reject-" + UUID.randomUUID(), "300.00");

        assertThat(result.transfer().status()).isEqualTo("REVERSED");
        assertThat(result.transfer().failureReason()).contains("rejected");
        assertThat(balanceOf(ada)).isEqualTo(100_000);
        assertThat(balanceOfNumber(CLEARING_NUMBER)).isEqualTo(clearingBefore);
    }

    @Test
    void timeoutLeavesFundsHeldAndNeverRefundsOnItsOwn() {
        AccountView ada = fundedAccount(100_000);
        fake.setMode(Mode.TIMEOUT_NOT_PROCESSED);

        TransferResult result = send(ada, "timeout-" + UUID.randomUUID(), "300.00");

        assertThat(result.transfer().status()).isEqualTo("PROCESSING");
        assertThat(balanceOf(ada)).isEqualTo(70_000);   // still held, not refunded
    }

    @Test
    void timeoutThenProviderHasNoRecordIsSafelyReversed() {
        AccountView ada = fundedAccount(100_000);
        fake.setMode(Mode.TIMEOUT_NOT_PROCESSED);
        TransferResult result = send(ada, "norecord-" + UUID.randomUUID(), "300.00");

        TransferView refreshed = externalTransferApi.refresh(result.transfer().id());

        assertThat(refreshed.status()).isEqualTo("REVERSED");
        assertThat(balanceOf(ada)).isEqualTo(100_000);
    }

    @Test
    void timeoutButProviderActuallyPaidIsCompletedNotRefunded() {
        AccountView ada = fundedAccount(100_000);
        fake.setMode(Mode.TIMEOUT_BUT_PROCESSED);
        TransferResult result = send(ada, "paid-" + UUID.randomUUID(), "300.00");
        assertThat(result.transfer().status()).isEqualTo("PROCESSING");

        TransferView refreshed = externalTransferApi.refresh(result.transfer().id());

        assertThat(refreshed.status()).isEqualTo("COMPLETED");
        assertThat(balanceOf(ada)).isEqualTo(70_000);   // the dangerous case: no refund
    }

    @Test
    void pendingStaysProcessingUntilProviderFinishes() {
        AccountView ada = fundedAccount(100_000);
        fake.setMode(Mode.ACCEPT_PENDING);
        TransferResult result = send(ada, "pending-" + UUID.randomUUID(), "300.00");
        assertThat(result.transfer().status()).isEqualTo("PROCESSING");

        assertThat(externalTransferApi.refresh(result.transfer().id()).status()).isEqualTo("PROCESSING");

        fake.settleAtProvider("trf_" + result.transfer().id(), GatewayTransferStatus.SUCCESS);
        assertThat(externalTransferApi.refresh(result.transfer().id()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void refreshingASettledTransferChangesNothing() {
        AccountView ada = fundedAccount(100_000);
        fake.setMode(Mode.REJECT);
        TransferResult result = send(ada, "twice-" + UUID.randomUUID(), "300.00");

        externalTransferApi.refresh(result.transfer().id());
        externalTransferApi.refresh(result.transfer().id());

        assertThat(balanceOf(ada)).isEqualTo(100_000);   // refunded exactly once
    }

    @Test
    void replayDoesNotCallTheProviderOrDebitTwice() {
        AccountView ada = fundedAccount(100_000);
        String key = "replay-" + UUID.randomUUID();

        TransferResult first = send(ada, key, "300.00");
        TransferResult second = send(ada, key, "300.00");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().id()).isEqualTo(first.transfer().id());
        assertThat(fake.initiateCalls()).isEqualTo(1);
        assertThat(balanceOf(ada)).isEqualTo(70_000);
    }

    @Test
    void insufficientFundsNeverReachesTheProvider() {
        AccountView ada = fundedAccount(10_000);   // N100

        assertThatThrownBy(() -> send(ada, "poor-" + UUID.randomUUID(), "5000.00"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));

        assertThat(fake.initiateCalls()).isZero();
        assertThat(balanceOf(ada)).isEqualTo(10_000);
    }

    // ---- helpers ----

    private TransferResult send(AccountView from, String key, String amount) {
        return externalTransferApi.transferExternal(new ExternalTransferCommand(
                key, from.accountNumber(), "0123456789", "058", new BigDecimal(amount), "test payout"));
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
        return balanceOfNumber(account.accountNumber());
    }

    private long balanceOfNumber(String accountNumber) {
        return accountApi.getByAccountNumber(accountNumber).balance().minorUnits();
    }
}