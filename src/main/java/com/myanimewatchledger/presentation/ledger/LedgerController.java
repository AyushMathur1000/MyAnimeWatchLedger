package com.myanimewatchledger.presentation.ledger;

import com.myanimewatchledger.application.service.LedgerChangeEvent;
import com.myanimewatchledger.application.service.LedgerService;
import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Thin presentation controller for permanent ledger operations. */
public final class LedgerController {
    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public List<LedgerEntry> loadEntries() {
        return service.getAll();
    }

    public Optional<LedgerEntry> find(long malId) {
        return service.find(malId);
    }

    public boolean changeStatus(long malId, LedgerStatus status) {
        return service.changeStatus(malId, status);
    }

    public boolean remove(long malId) {
        return service.remove(malId);
    }

    public AutoCloseable subscribeToLedgerChanges(Consumer<LedgerChangeEvent> listener) {
        return service.subscribe(listener);
    }
}
