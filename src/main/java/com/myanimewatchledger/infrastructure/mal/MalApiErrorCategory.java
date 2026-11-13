package com.myanimewatchledger.infrastructure.mal;

/** Stable categories for failures returned by or encountered while calling MAL. */
public enum MalApiErrorCategory {
    REJECTED_CREDENTIALS,
    RATE_LIMITED,
    SERVICE_ERROR,
    HTTP_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    INTERRUPTED
}
