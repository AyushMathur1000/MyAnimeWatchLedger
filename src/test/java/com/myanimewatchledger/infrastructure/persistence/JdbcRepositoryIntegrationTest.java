package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JdbcRepositoryIntegrationTest {
 @TempDir Path temporaryDirectory;
 private DatabaseManager database;
 private DatabaseTransactionManager transactions;
 private JdbcLedgerRepository ledger;
 private JdbcCatalogueRepository catalogue;
 private JdbcAnimeMetadataRepository metadata;
 private JdbcOpenCatalogueTabRepository tabs;

 @BeforeEach void setUp() throws Exception {
  database=new DatabaseManager(temporaryDirectory.resolve("ledger.db"));database.initialize();
  new DatabaseMigrationRunner(database).migrate();transactions=new DatabaseTransactionManager(database);
  ledger=new JdbcLedgerRepository(transactions);catalogue=new JdbcCatalogueRepository(transactions);
  metadata=new JdbcAnimeMetadataRepository(transactions);tabs=new JdbcOpenCatalogueTabRepository(transactions);
 }
 @AfterEach void tearDown(){if(database!=null)database.close();}

 @Test void ledgerCrudAndTitleSearchWork(){
  Instant now=Instant.now();LedgerEntry entry=new LedgerEntry(1001,"Steins;Gate",LedgerStatus.WATCHING,now,now);
  ledger.add(entry);assertEquals(entry,ledger.findByMalId(1001).orElseThrow());
  assertEquals(1,ledger.searchByTitle("steins").size());assertTrue(ledger.updateStatus(1001,LedgerStatus.WATCHED));
  assertEquals(LedgerStatus.WATCHED,ledger.findByMalId(1001).orElseThrow().status());
  assertTrue(ledger.updatePreferredTitle(1001,"Steins;Gate 0"));
  assertEquals("Steins;Gate 0",ledger.findByMalId(1001).orElseThrow().title());
  assertTrue(ledger.remove(1001));assertTrue(ledger.findAll().isEmpty());
 }
 @Test void catalogueFiltersBlacklistedRowsAndAppliesAndTokens(){
  var search=catalogue.findOrCreate(Season.WINTER,2026);
  metadata.upsert(new AnimeMetadataCache(2001,"Alpha Beta","{\"title\":\"Alpha Beta\"}","alpha beta fantasy",Instant.now()));
  metadata.upsert(new AnimeMetadataCache(2002,"Alpha Gamma","{\"title\":\"Alpha Gamma\"}","alpha gamma fantasy",Instant.now()));
  catalogue.replaceMembership(search.searchId(),List.of(2001L,2002L));Instant now=Instant.now();
  ledger.add(new LedgerEntry(2002,"Alpha Gamma",LedgerStatus.BLACKLISTED,now,now));
  var rows=catalogue.findEligibleAnime(search.searchId(),List.of("alpha","fantasy"));
  assertEquals(1,rows.size());assertEquals(2001L,rows.getFirst().malId());
 }
 @Test void openTabsCanBeRestoredAndClosed(){
  var search=catalogue.findOrCreate(Season.SUMMER,2025);
  tabs.save(new OpenCatalogueTab(search.searchId(),0,"mecha"));
  assertEquals("mecha",tabs.findAllOrdered().getFirst().searchQuery());
  assertTrue(tabs.close(search.searchId()));assertTrue(tabs.findAllOrdered().isEmpty());
 }
 @Test void repositoriesJoinOneTransactionAndRollbackTogether(){
  var search=catalogue.findOrCreate(Season.FALL,2026);Instant now=Instant.now();
  assertThrows(IllegalStateException.class,()->transactions.executeInTransaction(()->{
   ledger.add(new LedgerEntry(3001,"Rollback Anime",LedgerStatus.HOLD,now,now));
   metadata.upsert(new AnimeMetadataCache(3001,"Rollback Anime","{\"title\":\"Rollback Anime\"}","rollback anime",now));
   catalogue.replaceMembership(search.searchId(),List.of(3001L));
   throw new IllegalStateException("force rollback");
  }));
  assertTrue(ledger.findByMalId(3001).isEmpty());assertTrue(metadata.findByMalId(3001).isEmpty());
  assertTrue(catalogue.findEligibleAnime(search.searchId(),List.of()).isEmpty());
 }
 @Test void cacheClearNeverDeletesPermanentLedgerRows(){
  Instant now=Instant.now();ledger.add(new LedgerEntry(4001,"Permanent Entry",LedgerStatus.WATCHED,now,now));
  var search=catalogue.findOrCreate(Season.SPRING,2024);
  metadata.upsert(new AnimeMetadataCache(4001,"Permanent Entry","{\"title\":\"Permanent Entry\"}","permanent entry",now));
  catalogue.replaceMembership(search.searchId(),List.of(4001L));tabs.save(new OpenCatalogueTab(search.searchId(),0,""));
  catalogue.clearCache();
  assertTrue(ledger.findByMalId(4001).isPresent());assertTrue(catalogue.findAll().isEmpty());assertTrue(tabs.findAllOrdered().isEmpty());
 }
 @Test void invalidMetadataJsonIsRejectedBeforePersistence(){
  assertThrows(IllegalArgumentException.class,()->metadata.upsert(new AnimeMetadataCache(5001,"Bad","not-json","bad",Instant.now())));
  assertTrue(metadata.findByMalId(5001).isEmpty());
 }
}
