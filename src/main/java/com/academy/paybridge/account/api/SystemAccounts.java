package com.academy.paybridge.account.api;

import java.util.UUID;

/** Well-known IDs of the system accounts seeded by migration V3. */
public final class SystemAccounts {

    public static final UUID SANDBOX_FUNDING_NGN =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID PAYSTACK_CLEARING_NGN =
            UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private SystemAccounts() {
    }
}