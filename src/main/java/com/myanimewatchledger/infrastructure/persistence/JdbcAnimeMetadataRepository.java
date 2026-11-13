package com.myanimewatchledger.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.port.AnimeMetadataRepository;
import com.myanimewatchledger.domain.catalogue.AnimeMetadataCache;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JdbcAnimeMetadataRepository implements AnimeMetadataRepository {
 private final DatabaseTransactionManager transactions;
 private final ObjectMapper mapper=new ObjectMapper();
 public JdbcAnimeMetadataRepository(DatabaseTransactionManager transactions){this.transactions=Objects.requireNonNull(transactions);}

 @Override public void upsert(AnimeMetadataCache item) {
  validateJson(item.metadataJson());
  transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement(upsertSql())){bind(s,item);s.executeUpdate();} return null;
  });
 }
 @Override public void upsertAll(Collection<AnimeMetadataCache> items) {
  Objects.requireNonNull(items);
  for(AnimeMetadataCache item:items)validateJson(item.metadataJson());
  transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement(upsertSql())){
    for(AnimeMetadataCache item:items){bind(s,item);s.addBatch();}
    s.executeBatch();
   } return null;
  });
 }
 @Override public Optional<AnimeMetadataCache> findByMalId(long id) {
  return transactions.withConnection(c -> {
   try(PreparedStatement s=c.prepareStatement("SELECT mal_id,preferred_title,metadata_json,searchable_text,cached_at FROM anime_metadata_cache WHERE mal_id=?")){
    s.setLong(1,id);try(ResultSet r=s.executeQuery()){
     if(!r.next())return Optional.empty();
     return Optional.of(new AnimeMetadataCache(r.getLong("mal_id"),r.getString("preferred_title"),r.getString("metadata_json"),
      r.getString("searchable_text"),Instant.ofEpochMilli(r.getLong("cached_at"))));
    }
   }
  });
 }
 @Override public void deleteAll(){transactions.withConnection(c->{try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM anime_metadata_cache");}return null;});}
 private void validateJson(String json){
  try{JsonNode node=mapper.readTree(json);if(node==null||!node.isObject())throw new IllegalArgumentException("Metadata JSON must be a JSON object.");}
  catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Metadata JSON is invalid.",e);}
 }
 private static String upsertSql(){return """
  INSERT INTO anime_metadata_cache(mal_id,preferred_title,metadata_json,searchable_text,cached_at) VALUES(?,?,?,?,?)
  ON CONFLICT(mal_id) DO UPDATE SET preferred_title=excluded.preferred_title,metadata_json=excluded.metadata_json,
  searchable_text=excluded.searchable_text,cached_at=excluded.cached_at
  """;}
 private static void bind(PreparedStatement s,AnimeMetadataCache m)throws SQLException{
  s.setLong(1,m.malId());s.setString(2,m.preferredTitle());s.setString(3,m.metadataJson());s.setString(4,m.searchableText());s.setLong(5,m.cachedAt().toEpochMilli());
 }
}
