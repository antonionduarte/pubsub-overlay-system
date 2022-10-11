package asd.protocols.overlay.kad.query;

import java.util.Optional;

import asd.protocols.overlay.kad.KadID;

public interface FindValueQueryCallbacks {
    void onQueryResult(Optional<KadID> closest, Optional<byte[]> value);
}
