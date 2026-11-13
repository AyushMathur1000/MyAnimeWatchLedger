package com.myanimewatchledger.application.port;
import com.myanimewatchledger.domain.catalogue.OpenCatalogueTab;
import java.util.List;
public interface OpenCatalogueTabRepository {
 void save(OpenCatalogueTab tab);
 List<OpenCatalogueTab> findAllOrdered();
 void replaceAll(List<OpenCatalogueTab> tabs);
 boolean close(long searchId);
 void clearAll();
}
