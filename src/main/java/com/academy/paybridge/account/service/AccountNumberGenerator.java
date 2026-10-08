package com.academy.paybridge.account.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates 10-digit account numbers in NUBAN(Nigerian Uniform Bank Account Number) style:
 * 9-digit serial number + 1 check digit.
 */
@Component
class AccountNumberGenerator {

    // placeholder bank codes for PayBridge. Real Banks have their own CBN assigned codes.
    private static final String BANK_CODE = "999";
    private static final int[] WEIGHTS= {3,7,3,3,7,3,3,7,3,3,7,3};

    private final SecureRandom random = new SecureRandom();

    String next(){
        String serial = String.format("%09d", random.nextInt(1_000_000_000));
        return serial + checkDigit(serial);
    }

    static int checkDigit(String serial){
        String digits = BANK_CODE + serial;
        int sum = 0;
        for (int i = 0; i < digits.length(); i++){
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 10 - (sum % 10);
        return check == 10 ? 0 : check;
    }
}
