package com.academy.paybridge.ledger.api;

import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Currency;

import java.util.List;
import java.util.Objects;

/**
 * A request to record a balanced set of ledger entries as one atomic unit.
 * It is impossible to construct an unbalanced one.
 */
public record PostingRequest(
        String reference,
        LedgerTransactionType type,
        String description,
        List<PostingLine> lines
) {

    public PostingRequest{
        if (reference == null || reference.isBlank()){
            throw new BusinessException("INVALID_POSTING", "A posting reference is required");
        }
        Objects.requireNonNull(type, "type is required");

        if (lines == null || lines.size() < 2){
            throw new BusinessException("INVALID_POSTING", "A posting needs at least two lines");
        }

        lines = List.copyOf(lines);

        Currency currency = lines.get(0).amount().currency();
        long debits = 0;
        long credits = 0;

        for (PostingLine line : lines){
            if (line.amount().currency() != currency){
                throw new BusinessException("CURRENCY_MISMATCH", "All lines of a posting must be in the same currency");
            }
            if (!line.amount().isPositive()){
                throw new BusinessException("INVALID_AMOUNT", "Every line amount must be greater than zero");
            }
            switch (line.direction()){
                case DEBIT -> debits = Math.addExact(debits, line.amount().minorUnits());
                case CREDIT -> credits = Math.addExact(credits, line.amount().minorUnits());
            }
        }
        if (debits != credits){
            throw new BusinessException("UNBALANCED_POSTING",
                    "Debits(" + debits + ") must equal credits (" + credits + ")");
        }
    }

    public Currency currency(){
        return lines.get(0).amount().currency();
    }
}
