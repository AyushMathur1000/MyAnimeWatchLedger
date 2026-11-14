package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.ledger.LedgerEntry;
import com.myanimewatchledger.domain.ledger.LedgerStatus;

/** Describes a successfully persisted change to one permanent ledger entry. */
public record LedgerChangeEvent(Type type, long malId, LedgerEntry entry, LedgerStatus previousStatus) {
    public enum Type { ADDED, STATUS_CHANGED, REMOVED }
}
