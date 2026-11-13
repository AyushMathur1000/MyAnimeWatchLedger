package com.myanimewatchledger.application.service;
import com.myanimewatchledger.application.port.*;
import com.myanimewatchledger.domain.catalogue.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CatalogueTabServiceTest {
 @Test void reopeningClosedSearchCreatesTabWithExistingSearchIdentity(){FakeCatalogue c=new FakeCatalogue();FakeTabs t=new FakeTabs();var service=new CatalogueTabService(c,t);assertEquals(new OpenCatalogueTab(12,0,""),service.openSearch(12));assertEquals(Season.SUMMER,c.findById(12).orElseThrow().season());}
 @Test void reopeningAlreadyOpenSearchPreservesQueryAndOrder(){FakeTabs t=new FakeTabs();t.rows.add(new OpenCatalogueTab(12,2,"space opera"));var result=new CatalogueTabService(new FakeCatalogue(),t).openSearch(12);assertEquals(new OpenCatalogueTab(12,2,"space opera"),result);assertEquals(1,t.rows.size());}
 @Test void unknownSearchCannotBeReopened(){assertThrows(IllegalArgumentException.class,()->new CatalogueTabService(new FakeCatalogue(),new FakeTabs()).openSearch(999));}
 private static class FakeCatalogue implements CatalogueRepository{
  CatalogueSearch s=new CatalogueSearch(12,Season.SUMMER,2025,null,Instant.parse("2025-06-02T00:00:00Z"),FetchStatus.SUCCESS,null);
  public CatalogueSearch findOrCreate(Season x,int y){return s;}public Optional<CatalogueSearch> findById(long id){return id==12?Optional.of(s):Optional.empty();}public List<CatalogueSearch> findAll(){return List.of(s);}
  public void markFetchSucceeded(long id,Instant at){}public void markFetchFailed(long id,Instant at,String error){}public void replaceMembership(long id,List<Long> ids){}public List<CatalogueAnime> findEligibleAnime(long id,List<String> tokens){return List.of();}public void clearCache(){}
 }
 private static class FakeTabs implements OpenCatalogueTabRepository{
  List<OpenCatalogueTab> rows=new ArrayList<>();public void save(OpenCatalogueTab t){rows.removeIf(x->x.searchId()==t.searchId());rows.add(t);}public List<OpenCatalogueTab> findAllOrdered(){return List.copyOf(rows);}public void replaceAll(List<OpenCatalogueTab> t){rows.clear();rows.addAll(t);}public boolean close(long id){return rows.removeIf(t->t.searchId()==id);}public void clearAll(){rows.clear();}
 }
}