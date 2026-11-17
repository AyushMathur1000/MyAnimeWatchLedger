package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.LedgerRepository;
import com.myanimewatchledger.domain.ledger.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JdbcLedgerRepository implements LedgerRepository {
 private static final String COLUMNS = "mal_id, title, status, date_added, last_modified, preference, watch_later_priority";
 private final DatabaseTransactionManager transactions;
 public JdbcLedgerRepository(DatabaseTransactionManager transactions) { this.transactions = Objects.requireNonNull(transactions); }

 @Override public void add(LedgerEntry entry) {
  transactions.withConnection(c -> {
   try (PreparedStatement s = c.prepareStatement("INSERT INTO ledger_entry ("+COLUMNS+") VALUES (?, ?, ?, ?, ?, ?, ?)")) {
    s.setLong(1,entry.malId()); s.setString(2,entry.title()); s.setString(3,entry.status().name());
    s.setLong(4,entry.dateAdded().toEpochMilli()); s.setLong(5,entry.lastModified().toEpochMilli());
    s.setString(6, entry.preference() == null ? null : entry.preference().name());
    if (entry.watchLaterPriority() == null) s.setNull(7, Types.INTEGER); else s.setInt(7, entry.watchLaterPriority());
    s.executeUpdate();
   } return null;
  });
 }
 @Override public Optional<LedgerEntry> findByMalId(long id) {
  return transactions.withConnection(c -> {
   try (PreparedStatement s=c.prepareStatement("SELECT "+COLUMNS+" FROM ledger_entry WHERE mal_id=?")) {
    s.setLong(1,id); try(ResultSet r=s.executeQuery()){ return r.next()?Optional.of(map(r)):Optional.empty(); }
   }
  });
 }
 @Override public List<LedgerEntry> findAll() { return query("SELECT "+COLUMNS+" FROM ledger_entry ORDER BY title COLLATE NOCASE",null); }
 @Override public List<LedgerEntry> findByStatus(LedgerStatus status) {
  Objects.requireNonNull(status);
  return query("SELECT "+COLUMNS+" FROM ledger_entry WHERE status=? ORDER BY title COLLATE NOCASE",status.name());
 }
 @Override public List<LedgerEntry> searchByTitle(String query) {
  String value=query==null?"":query.trim();
  return query("SELECT "+COLUMNS+" FROM ledger_entry WHERE instr(lower(title),lower(?))>0 ORDER BY title COLLATE NOCASE",value);
 }
 @Override public boolean updateStatus(long id, LedgerStatus status) {
  Objects.requireNonNull(status);
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("UPDATE ledger_entry SET status=?, last_modified=?, watch_later_priority=CASE WHEN ?='WATCH_LATER' THEN watch_later_priority ELSE NULL END WHERE mal_id=? AND status<>?")) {
    s.setString(1,status.name()); s.setLong(2,Instant.now().toEpochMilli()); s.setString(3,status.name()); s.setLong(4,id); s.setString(5,status.name()); return s.executeUpdate()>0;
   }
  });
 }
 @Override public boolean updatePreferredTitle(long id,String title) {
  if(title==null||title.isBlank()) throw new IllegalArgumentException("Title is required.");
  String normalized=title.trim();
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("UPDATE ledger_entry SET title=?, last_modified=? WHERE mal_id=? AND title<>?")) {
    s.setString(1,normalized); s.setLong(2,Instant.now().toEpochMilli()); s.setLong(3,id); s.setString(4,normalized); return s.executeUpdate()>0;
   }
  });
 }
 @Override public boolean updatePreference(long id, AnimePreference preference) {
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("UPDATE ledger_entry SET preference=?, last_modified=? WHERE mal_id=? AND (preference IS NOT ?)")) {
    if(preference==null)s.setNull(1,Types.VARCHAR);else s.setString(1,preference.name());
    s.setLong(2,Instant.now().toEpochMilli()); s.setLong(3,id);
    if(preference==null)s.setNull(4,Types.VARCHAR);else s.setString(4,preference.name());
    return s.executeUpdate()>0;
   }
  });
 }
 @Override public boolean updateWatchLaterPriority(long id, Integer priority) {
  if(priority!=null&&(priority<1||priority>10)) throw new IllegalArgumentException("Priority must be between 1 and 10.");
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("UPDATE ledger_entry SET watch_later_priority=?, last_modified=? WHERE mal_id=? AND status='WATCH_LATER' AND (watch_later_priority IS NOT ?)")) {
    if(priority==null)s.setNull(1,Types.INTEGER);else s.setInt(1,priority);
    s.setLong(2,Instant.now().toEpochMilli()); s.setLong(3,id);
    if(priority==null)s.setNull(4,Types.INTEGER);else s.setInt(4,priority);
    return s.executeUpdate()>0;
   }
  });
 }
 @Override public boolean remove(long id) {
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("DELETE FROM ledger_entry WHERE mal_id=?")) { s.setLong(1,id); return s.executeUpdate()>0; }
  });
 }
 private List<LedgerEntry> query(String sql,String parameter) {
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement(sql)) {
    if(parameter!=null)s.setString(1,parameter);
    try(ResultSet r=s.executeQuery()){ List<LedgerEntry> rows=new ArrayList<>(); while(r.next())rows.add(map(r)); return rows; }
   }
  });
 }
 private static LedgerEntry map(ResultSet r)throws SQLException {
  return new LedgerEntry(r.getLong("mal_id"),r.getString("title"),LedgerStatus.valueOf(r.getString("status")),
   Instant.ofEpochMilli(r.getLong("date_added")),Instant.ofEpochMilli(r.getLong("last_modified")),
   r.getString("preference") == null ? null : AnimePreference.valueOf(r.getString("preference")),
   (Integer) r.getObject("watch_later_priority"));
 }
}
