package com.myanimewatchledger.infrastructure.mal;

import java.net.URI;

/** Minimal transport contract used by the MAL response reader and its tests. */
public interface MalApiTransport {
    String get(String relativePathAndQuery);
    String getNextPage(URI nextPageUri);
}
