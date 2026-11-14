package com.myanimewatchledger.infrastructure.mal;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stable transport facade whose MAL client can be replaced when the user changes
 * the Client ID in Settings. Services keep this facade for their whole lifetime.
 */
public final class SwitchableMalApiTransport implements MalApiTransport {
    private final AtomicReference<MalApiTransport> delegate = new AtomicReference<>();

    public void setDelegate(MalApiTransport transport) {
        delegate.set(Objects.requireNonNull(transport, "transport"));
    }

    public boolean isConfigured() {
        return delegate.get() != null;
    }

    @Override
    public String get(String relativePathAndQuery) {
        return current().get(relativePathAndQuery);
    }

    @Override
    public String getNextPage(URI nextPageUri) {
        return current().getNextPage(nextPageUri);
    }

    private MalApiTransport current() {
        MalApiTransport current = delegate.get();
        if (current == null) {
            throw new IllegalStateException("MAL Client ID is not configured.");
        }
        return current;
    }
}
