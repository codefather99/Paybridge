package com.academy.paybridge.transfer.service;

import com.academy.paybridge.transfer.client.TransferGateway;
import com.academy.paybridge.transfer.client.TransferGateway.Bank;
import com.academy.paybridge.transfer.client.TransferGateway.ResolvedAccount;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class BankLookupService {

    private final TransferGateway gateway;

    public BankLookupService(TransferGateway gateway) {
        this.gateway = gateway;
    }

    public List<Bank> searchBanks(String nameFilter) {
        List<Bank> banks = gateway.listBanks();
        if (nameFilter == null || nameFilter.isBlank()) {
            return banks;
        }
        String needle = nameFilter.toLowerCase(Locale.ROOT);
        return banks.stream()
                .filter(b -> b.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    public ResolvedAccount resolve(String accountNumber, String bankCode) {
        return gateway.resolveAccount(accountNumber, bankCode);
    }
}