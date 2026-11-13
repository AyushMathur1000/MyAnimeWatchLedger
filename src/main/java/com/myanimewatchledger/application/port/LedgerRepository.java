package com.myanimewatchledger.application.port;
import com.myanimewatchledger.domain.ledger.*;
import java.util.*;
public interface LedgerRepository {
 void add(LedgerEntry entry);
 Optional<LedgerEntry> findByMalId(long malId);
 List<LedgerEntry> findAll();
 List<LedgerEntry> findByStatus(LedgerStatus status);
 List<LedgerEntry> searchByTitle(String query);
 boolean updateStatus(long malId, LedgerStatus status);
 boolean updatePreferredTitle(long malId, String title);
 boolean remove(long malId);
}
