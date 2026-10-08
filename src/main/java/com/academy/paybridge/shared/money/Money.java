package com.academy.paybridge.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;


/**
* An amount of money in the smallest
* unit of is currency. ( Kobo for NGN, cents for USD).
* It's immutable.
 **/

public record Money(long minorUnits, Currency currency) {

    public Money{
        Objects.requireNonNull(currency, "currency is required");
    }

    public static Money zero(Currency currency){
        return new Money(0, currency);
    }

    /** Build from a major-unit decimal such as 1500.50 Naira. Rejects sub-kobo precision **/
    public static Money ofMajor(BigDecimal major, Currency currency){
        BigDecimal minor = major.movePointRight(2);
        if(minor.stripTrailingZeros().scale() > 0){
            throw new IllegalArgumentException("Amount has more than 2 decimal places");
        }
        return new Money(minor.setScale(0, RoundingMode.UNNECESSARY).longValueExact(), currency);
    }

    public Money plus(Money other){
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other){
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isPositive(){
        return minorUnits > 0;
    }

    public boolean isLessThan(Money other){
        requireSameCurrency(other);
        return minorUnits < other.minorUnits;
    }

    public BigDecimal toMajor(){
        return BigDecimal.valueOf(minorUnits, 2);
    }

    private void requireSameCurrency(Money other){
        if (currency != other.currency){
            throw new IllegalArgumentException("Currency mismatch: " + currency + " Vs " + other.currency);
        }
    }

}
