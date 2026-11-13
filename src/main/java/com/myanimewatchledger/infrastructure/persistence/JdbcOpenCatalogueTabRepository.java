package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.OpenCatalogueTabRepository;
import com.myanimewatchledger.domain.catalogue.OpenCatalogueTab;
import java.sql.*;
import java.util.*;

public final class JdbcOpenCatalogueTabRepository implements OpenCatalogueTabRepository {
 private final DatabaseTransactionManager transactions;
 public JdbcOpenCatalogueTabRepository(DatabaseTransactionManager transactions){this.transactions=Objects.requireNonNull(transactions);}
 @Override public void save(OpenCatalogueTab tab){
  transactions.withConnection(c->{try(PreparedStatement s=c.prepareStatement("""
   INSERT INTO open_catalogue_tab(search_id,tab_order,search_query) VALUES(?,?,?)
   ON CONFLICT(search_id) DO UPDATE SET tab_order=excluded.tab_order,search_query=excluded.search_query
   """)){s.setLong(1,tab.searchId());s.setInt(2,tab.tabOrder());s.setString(3,tab.searchQuery());s.executeUpdate();}return null;});
 }
 @Override public List<OpenCatalogueTab> findAllOrdered(){
  return transactions.withConnection(c->{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
   "SELECT search_id,tab_order,search_query FROM open_catalogue_tab ORDER BY tab_order")){
   List<OpenCatalogueTab> tabs=new ArrayList<>();while(r.next())tabs.add(new OpenCatalogueTab(r.getLong(1),r.getInt(2),r.getString(3)));return tabs;
  }});
 }
 @Override public void replaceAll(List<OpenCatalogueTab> tabs){
  Objects.requireNonNull(tabs);
  transactions.executeInTransaction(()->{transactions.withConnection(c->{
   try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM open_catalogue_tab");}
   try(PreparedStatement s=c.prepareStatement("INSERT INTO open_catalogue_tab(search_id,tab_order,search_query) VALUES(?,?,?)")){
    for(OpenCatalogueTab tab:tabs){s.setLong(1,tab.searchId());s.setInt(2,tab.tabOrder());s.setString(3,tab.searchQuery());s.addBatch();}s.executeBatch();
   }return null;
  });return null;});
 }
 @Override public boolean close(long id){return transactions.withConnection(c->{try(PreparedStatement s=c.prepareStatement("DELETE FROM open_catalogue_tab WHERE search_id=?")){s.setLong(1,id);return s.executeUpdate()>0;}});}
 @Override public void clearAll(){transactions.withConnection(c->{try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM open_catalogue_tab");}return null;});}
}
