package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.CatalogueRepository;
import com.myanimewatchledger.domain.catalogue.*;
import com.myanimewatchledger.domain.ledger.LedgerStatus;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JdbcCatalogueRepository implements CatalogueRepository {
 private final DatabaseTransactionManager transactions;
 public JdbcCatalogueRepository(DatabaseTransactionManager transactions){this.transactions=Objects.requireNonNull(transactions);}

 @Override public CatalogueSearch findOrCreate(Season season,int year){
  Objects.requireNonNull(season);if(year<1900||year>9999)throw new IllegalArgumentException("Year is out of range.");
  return transactions.withConnection(c->{
   try(PreparedStatement s=c.prepareStatement("INSERT INTO catalogue_search(season,year,fetch_status) VALUES(?,?,'NEVER_FETCHED') ON CONFLICT(season,year) DO NOTHING")){
    s.setString(1,season.name());s.setInt(2,year);s.executeUpdate();
   }
   try(PreparedStatement s=c.prepareStatement(searchSelect()+" WHERE season=? AND year=?")){
    s.setString(1,season.name());s.setInt(2,year);try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Season search was not created.");return mapSearch(r);}
   }
  });
 }
 @Override public Optional<CatalogueSearch> findById(long id){
  return transactions.withConnection(c->{try(PreparedStatement s=c.prepareStatement(searchSelect()+" WHERE search_id=?")){
   s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(mapSearch(r)):Optional.empty();}
  }});
 }
 @Override public List<CatalogueSearch> findAll(){
  return transactions.withConnection(c->{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(searchSelect()+" ORDER BY year DESC, season")){
   List<CatalogueSearch> rows=new ArrayList<>();while(r.next())rows.add(mapSearch(r));return rows;
  }});
 }
 @Override public void markFetchSucceeded(long id,Instant at){
  Objects.requireNonNull(at);transactions.withConnection(c->{try(PreparedStatement s=c.prepareStatement("""
   UPDATE catalogue_search SET last_attempt_at=?,last_successful_fetch_at=?,fetch_status='SUCCESS',last_error=NULL WHERE search_id=?
   """)){s.setLong(1,at.toEpochMilli());s.setLong(2,at.toEpochMilli());s.setLong(3,id);if(s.executeUpdate()==0)throw new SQLException("Unknown search ID: "+id);}return null;});
 }
 @Override public void markFetchFailed(long id,Instant at,String error){
  Objects.requireNonNull(at);transactions.withConnection(c->{try(PreparedStatement s=c.prepareStatement("""
   UPDATE catalogue_search SET last_attempt_at=?,fetch_status='FAILED',last_error=? WHERE search_id=?
   """)){s.setLong(1,at.toEpochMilli());s.setString(2,error==null?"Unknown catalogue fetch error":error);s.setLong(3,id);if(s.executeUpdate()==0)throw new SQLException("Unknown search ID: "+id);}return null;});
 }
 @Override public void replaceMembership(long id,List<Long> malIds){
  Objects.requireNonNull(malIds);
  transactions.executeInTransaction(()->{
   transactions.withConnection(c->{try(PreparedStatement d=c.prepareStatement("DELETE FROM catalogue_search_anime WHERE search_id=?")){d.setLong(1,id);d.executeUpdate();}
    try(PreparedStatement s=c.prepareStatement("INSERT INTO catalogue_search_anime(search_id,mal_id,display_order) VALUES(?,?,?)")){
     for(int i=0;i<malIds.size();i++){s.setLong(1,id);s.setLong(2,malIds.get(i));s.setInt(3,i);s.addBatch();}s.executeBatch();
    }return null;});
   return null;
  });
 }
 @Override public List<CatalogueAnime> findEligibleAnime(long id,List<String> tokens){
  List<String> terms=tokens==null?List.of():tokens.stream().filter(t->t!=null&&!t.isBlank()).toList();
  return transactions.withConnection(c->{
   StringBuilder sql=new StringBuilder("""
    SELECT m.mal_id,m.preferred_title,m.metadata_json,m.searchable_text,m.cached_at,s.display_order,l.status AS ledger_status
    FROM catalogue_search_anime s JOIN anime_metadata_cache m ON m.mal_id=s.mal_id
    LEFT JOIN ledger_entry l ON l.mal_id=m.mal_id
    WHERE s.search_id=? AND (l.mal_id IS NULL OR l.status<>'BLACKLISTED')
    """);
   for(int i=0;i<terms.size();i++)sql.append(" AND instr(lower(m.searchable_text),lower(?))>0");
   sql.append(" ORDER BY s.display_order");
   try(PreparedStatement s=c.prepareStatement(sql.toString())){
    s.setLong(1,id);for(int i=0;i<terms.size();i++)s.setString(i+2,terms.get(i));
    try(ResultSet r=s.executeQuery()){List<CatalogueAnime> rows=new ArrayList<>();while(r.next()){
     String status=r.getString("ledger_status");
     rows.add(new CatalogueAnime(r.getLong("mal_id"),r.getString("preferred_title"),r.getString("metadata_json"),
      r.getString("searchable_text"),Instant.ofEpochMilli(r.getLong("cached_at")),r.getInt("display_order"),
      status==null?null:LedgerStatus.valueOf(status)));
    }return rows;}
   }
  });
 }
 @Override public void clearCache(){
  transactions.executeInTransaction(()->{transactions.withConnection(c->{try(Statement s=c.createStatement()){
   s.executeUpdate("DELETE FROM open_catalogue_tab");s.executeUpdate("DELETE FROM catalogue_search_anime");
   s.executeUpdate("DELETE FROM catalogue_search");s.executeUpdate("DELETE FROM anime_metadata_cache");
  }return null;});return null;});
 }
 private static String searchSelect(){return "SELECT search_id,season,year,last_attempt_at,last_successful_fetch_at,fetch_status,last_error FROM catalogue_search";}
 private static CatalogueSearch mapSearch(ResultSet r)throws SQLException{
  long a=r.getLong("last_attempt_at");Instant attempted=r.wasNull()?null:Instant.ofEpochMilli(a);
  long f=r.getLong("last_successful_fetch_at");Instant fetched=r.wasNull()?null:Instant.ofEpochMilli(f);
  return new CatalogueSearch(r.getLong("search_id"),Season.valueOf(r.getString("season")),r.getInt("year"),
   attempted,fetched,FetchStatus.valueOf(r.getString("fetch_status")),r.getString("last_error"));
 }
}
