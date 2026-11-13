package com.myanimewatchledger.domain.catalogue;
public record OpenCatalogueTab(long searchId, int tabOrder, String searchQuery) {
 public OpenCatalogueTab {
  if (searchId <= 0 || tabOrder < 0) throw new IllegalArgumentException("Invalid tab identity or order.");
  searchQuery = searchQuery == null ? "" : searchQuery;
 }
}
