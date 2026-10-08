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
import com.academy.paybridge.transfer.api.TransferApi;
import com.academy.paybridge.transfer.api.TransferCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

    @Autowired
    AccountApi accountApi;
    @Autowired
    TransferApi transferApi;
    @Autowired
    LedgerApi ledgerApi;

    @Test
    void concurrentTransfersCannotOverdrawAnAccount() throws Exception {
        AccountView ada = fundedAccount(100_000);   // N1,000.00
        AccountView bola = emptyAccount();

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transferApi.transfer(command("race-" + UUID.randomUUID(), ada, bola, "300.00")));
        }
        List<Throwable> failures = runConcurrently(tasks);

        // Only three N300 transfers fit into N1,000. The other seven must be refused.
        assertThat(failures).hasSize(7).allSatisfy(t ->
                assertThat(t).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_FUNDS")));
        assertThat(balanceOf(ada)).isEqualTo(10_000);   // N100.00 left
        assertThat(balanceOf(bola)).isEqualTo(90_000);  // N900.00 received
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        AccountView ada = fundedAccount(1_000_000);   // N10,000.00
        AccountView bola = fundedAccount(1_000_000);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> transferApi.transfer(command("ab-" + UUID.randomUUID(), ada, bola, "10.00")));
            tasks.add(() -> transferApi.transfer(command("ba-" + UUID.randomUUID(), bola, ada, "10.00")));
        }
        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures).isEmpty();
        assertThat(balanceOf(ada)).isEqualTo(1_000_000);
        assertThat(balanceOf(bola)).isEqualTo(1_000_000);
    }

    @Test
    void sameIdempotencyKeySentConcurrentlyMovesMoneyOnce() throws Exception {
        AccountView ada = fundedAccount(100_000);
        AccountView bola = emptyAccount();
        String key = "same-key-" + UUID.randomUUID();

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> transferApi.transfer(command(key, ada, bola, "500.00")));
        }
        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures.size()).isLessThan(5);       // at least one request succeeded
        assertThat(balanceOf(ada)).isEqualTo(50_000);    // debited exactly once
        assertThat(balanceOf(bola)).isEqualTo(50_000);
    }

    // ---- helpers ----

    private AccountView emptyAccount() {
        return accountApi.openAccount(UUID.randomUUID(), Currency.NGN);
    }

    private AccountView fundedAccount(long kobo) {
        AccountView account = emptyAccount();
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

    private TransferCommand command(String key, AccountView from, AccountView to, String amount) {
        return new TransferCommand(key, from.accountNumber(), to.accountNumber(),
                new BigDecimal(amount), "concurrency test");
    }

    /** Starts every task at the same instant and returns whatever exceptions they threw. */
    private List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Runnable task : tasks) {
            futures.add(pool.submit(() -> {
                startGate.await();
                try {
                    task.run();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        startGate.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            try {
                Throwable failure = future.get(60, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            } catch (ExecutionException | TimeoutException e) {
                failures.add(e);
            }
        }
        pool.shutdown();
        return failures;
    }
}