package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.exception.BusinessException;

/** The provider received the request and definitely refused it. Nothing was sent. */
public class GatewayRejectedException extends BusinessException {

    private final int httpStatus;

    public GatewayRejectedException(int httpStatus, String message) {
        super("PROVIDER_REJECTED", message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}