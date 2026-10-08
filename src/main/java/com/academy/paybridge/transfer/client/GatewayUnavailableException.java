package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.exception.BusinessException;

/** We could not get a reliable answer. The outcome of the request is UNKNOWN. */
public class GatewayUnavailableException extends BusinessException {

    public GatewayUnavailableException(String message, Throwable cause) {
        super("PROVIDER_UNAVAILABLE", message);
        initCause(cause);
    }
}