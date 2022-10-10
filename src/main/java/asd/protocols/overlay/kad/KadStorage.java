package asd.protocols.overlay.kad;

import java.util.HashMap;
import java.util.Optional;

public class KadStorage {
    private class Value {
        public final KadID key;
        public final byte[] value;
        public final long last_refresh;

        public Value(KadID key, byte[] value, long last_refresh) {
            this.key = key;
            this.value = value;
            this.last_refresh = last_refresh;
        }
    }

    private final HashMap<KadID, Value> storage;

    public KadStorage() {
        this.storage = new HashMap<>();
    }

    public void store(KadID key, byte[] value) {
        this.storage.put(key, new Value(key, value, System.currentTimeMillis()));
    }

    public Optional<byte[]> get(KadID key) {
        Value value = this.storage.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value.value);
    }
}
