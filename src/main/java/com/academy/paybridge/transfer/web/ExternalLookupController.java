package com.academy.paybridge.transfer.web;

import com.academy.paybridge.transfer.client.TransferGateway.ResolvedAccount;
import com.academy.paybridge.transfer.service.BankLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banks")
@Tag(name = "Banks and name enquiry")
public class ExternalLookupController {

    private final BankLookupService lookupService;

    public ExternalLookupController(BankLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping
    @Operation(summary = "List Nigerian banks and their codes",
            description = "Optionally filter by part of the bank name.")
    public List<BankResponse> banks(
            @Parameter(description = "Part of a bank name", example = "access")
            @RequestParam(required = false) String name) {
        return lookupService.searchBanks(name).stream()
                .map(b -> new BankResponse(b.name(), b.code()))
                .toList();
    }

    @GetMapping("/resolve")
    @Operation(summary = "Name enquiry: who owns this bank account?")
    public NameEnquiryResponse resolve(
            @Parameter(description = "10-digit account number", example = "0123456789")
            @RequestParam @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String accountNumber,
            @Parameter(description = "Bank code from the banks list", example = "058")
            @RequestParam @NotBlank @Pattern(regexp = "\\d{3,6}", message = "must be 3 to 6 digits") String bankCode) {
        ResolvedAccount account = lookupService.resolve(accountNumber, bankCode);
        return new NameEnquiryResponse(account.accountNumber(), bankCode, account.accountName());
    }
}