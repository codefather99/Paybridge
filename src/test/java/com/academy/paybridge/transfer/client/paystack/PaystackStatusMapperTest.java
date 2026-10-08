package com.academy.paybridge.transfer.client.paystack;

import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackStatusMapperTest {

    @Test
    void mapsKnownStatuses() {
        assertThat(PaystackStatusMapper.map("success")).isEqualTo(GatewayTransferStatus.SUCCESS);
        assertThat(PaystackStatusMapper.map("failed")).isEqualTo(GatewayTransferStatus.FAILED);
        assertThat(PaystackStatusMapper.map("reversed")).isEqualTo(GatewayTransferStatus.REVERSED);
        assertThat(PaystackStatusMapper.map("otp")).isEqualTo(GatewayTransferStatus.ACTION_REQUIRED);
    }

    @Test
    void isCaseInsensitive() {
        assertThat(PaystackStatusMapper.map("SUCCESS")).isEqualTo(GatewayTransferStatus.SUCCESS);
    }

    @Test
    void unknownOrMissingStatusIsPendingNeverSuccessOrFailure() {
        assertThat(PaystackStatusMapper.map("something-new")).isEqualTo(GatewayTransferStatus.PENDING);
        assertThat(PaystackStatusMapper.map("pending")).isEqualTo(GatewayTransferStatus.PENDING);
        assertThat(PaystackStatusMapper.map(null)).isEqualTo(GatewayTransferStatus.PENDING);
    }
}