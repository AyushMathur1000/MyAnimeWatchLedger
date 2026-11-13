package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.*;
import java.util.Objects;

/**
 * Composition root for the JDBC persistence adapters. Every repository shares
 * one transaction manager so multi-repository units of work use one connection.
 */
public final class PersistenceContext {
 private final DatabaseTransactionManager transactions;
 private final LedgerRepository ledgerRepository;
 private final CatalogueRepository catalogueRepository;
 private final AnimeMetadataRepository animeMetadataRepository;
 private final OpenCatalogueTabRepository openCatalogueTabRepository;

 public PersistenceContext(DatabaseManager databaseManager){
  Objects.requireNonNull(databaseManager,"databaseManager");
  transactions=new DatabaseTransactionManager(databaseManager);
  ledgerRepository=new JdbcLedgerRepository(transactions);
  catalogueRepository=new JdbcCatalogueRepository(transactions);
  animeMetadataRepository=new JdbcAnimeMetadataRepository(transactions);
  openCatalogueTabRepository=new JdbcOpenCatalogueTabRepository(transactions);
 }

 public DatabaseTransactionManager transactions(){return transactions;}
 public LedgerRepository ledger(){return ledgerRepository;}
 public CatalogueRepository catalogue(){return catalogueRepository;}
 public AnimeMetadataRepository animeMetadata(){return animeMetadataRepository;}
 public OpenCatalogueTabRepository openCatalogueTabs(){return openCatalogueTabRepository;}
}
