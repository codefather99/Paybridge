package com.academy.paybridge.ledger;

import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingRequestTest {

    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BOLA = UUID.randomUUID();

    private static Money ngn(long kobo) {
        return new Money(kobo, Currency.NGN);
    }

    private static PostingRequest request(PostingLine... lines) {
        return new PostingRequest("ref-1", LedgerTransactionType.TRANSFER, "test", List.of(lines));
    }

    @Test
    void balancedPostingIsAccepted() {
        PostingRequest request = request(
                PostingLine.debit(ADA, ngn(200_000)),
                PostingLine.credit(BOLA, ngn(200_000)));

        assertThat(request.lines()).hasSize(2);
        assertThat(request.currency()).isEqualTo(Currency.NGN);
    }

    @Test
    void unbalancedPostingIsRejected() {
        assertThatThrownBy(() -> request(
                PostingLine.debit(ADA, ngn(200_000)),
                PostingLine.credit(BOLA, ngn(199_999))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNBALANCED_POSTING"));
    }

    @Test
    void singleLinePostingIsRejected() {
        assertThatThrownBy(() -> request(PostingLine.debit(ADA, ngn(100))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_POSTING"));
    }

    @Test
    void mixedCurrenciesAreRejected() {
        assertThatThrownBy(() -> request(
                PostingLine.debit(ADA, ngn(100)),
                PostingLine.credit(BOLA, new Money(100, Currency.USD))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CURRENCY_MISMATCH"));
    }

    @Test
    void zeroAmountLineIsRejected() {
        assertThatThrownBy(() -> request(
                PostingLine.debit(ADA, ngn(0)),
                PostingLine.credit(BOLA, ngn(0))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_AMOUNT"));
    }

    @Test
    void blankReferenceIsRejected() {
        assertThatThrownBy(() -> new PostingRequest(" ", LedgerTransactionType.TRANSFER, "test",
                List.of(PostingLine.debit(ADA, ngn(100)), PostingLine.credit(BOLA, ngn(100)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_POSTING"));
    }
}