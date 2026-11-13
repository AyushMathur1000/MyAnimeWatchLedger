package com.myanimewatchledger.application.service;
import com.myanimewatchledger.application.port.*;
import com.myanimewatchledger.domain.catalogue.*;
import java.util.*;
/** Manages restorable catalogue-tab state independently of search history. */
public final class CatalogueTabService {
 private final CatalogueRepository catalogue; private final OpenCatalogueTabRepository tabs;
 public CatalogueTabService(CatalogueRepository catalogue,OpenCatalogueTabRepository tabs){this.catalogue=Objects.requireNonNull(catalogue);this.tabs=Objects.requireNonNull(tabs);}
 public List<OpenCatalogueTab> restoreOpenTabs(){return tabs.findAllOrdered();}
 public OpenCatalogueTab open(Season season,int year,String query){CatalogueSearch s=catalogue.findOrCreate(Objects.requireNonNull(season),year);return openSearch(s.searchId(),query);}
 /** Reopens a known historical search without resetting an existing tab's query or order. */
 public OpenCatalogueTab openSearch(long id){return openSearch(id,null);}
 private OpenCatalogueTab openSearch(long id,String query){
  CatalogueSearch search=catalogue.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown catalogue search: "+id));
  List<OpenCatalogueTab> current=tabs.findAllOrdered();
  for(OpenCatalogueTab tab:current)if(tab.searchId()==id){
   if(query==null)return tab;
   OpenCatalogueTab updated=new OpenCatalogueTab(id,tab.tabOrder(),normalize(query));tabs.save(updated);return updated;
  }
  OpenCatalogueTab created=new OpenCatalogueTab(id,current.size(),query==null?"":normalize(query));tabs.save(created);return created;
 }
 public boolean updateQuery(long id,String query){for(OpenCatalogueTab tab:tabs.findAllOrdered())if(tab.searchId()==id){tabs.save(new OpenCatalogueTab(id,tab.tabOrder(),normalize(query)));return true;}return false;}
 public boolean close(long id){boolean removed=tabs.close(id);if(removed)compactOrder(tabs.findAllOrdered());return removed;}
 public void reorder(List<Long> ids){
  Objects.requireNonNull(ids);List<OpenCatalogueTab> current=tabs.findAllOrdered();Set<Long> existing=new HashSet<>();current.forEach(t->existing.add(t.searchId()));Set<Long> requested=new HashSet<>(ids);
  if(ids.size()!=requested.size()||!existing.equals(requested))throw new IllegalArgumentException("Tab order must contain every open tab exactly once.");
  List<OpenCatalogueTab> reordered=new ArrayList<>();for(int i=0;i<ids.size();i++){long id=ids.get(i);OpenCatalogueTab old=current.stream().filter(t->t.searchId()==id).findFirst().orElseThrow();reordered.add(new OpenCatalogueTab(id,i,old.searchQuery()));}tabs.replaceAll(reordered);
 }
 private void compactOrder(List<OpenCatalogueTab> current){List<OpenCatalogueTab> result=new ArrayList<>();for(int i=0;i<current.size();i++){OpenCatalogueTab t=current.get(i);result.add(new OpenCatalogueTab(t.searchId(),i,t.searchQuery()));}tabs.replaceAll(result);}
 private static String normalize(String q){return q==null?"":q.trim();}
}