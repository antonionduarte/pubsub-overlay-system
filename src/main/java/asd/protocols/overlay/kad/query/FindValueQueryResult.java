package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

import java.util.Optional;

public class FindValueQueryResult {
	public final Optional<KadID> closest;
	public final Optional<byte[]> value;

	public FindValueQueryResult(Optional<KadID> closest, Optional<byte[]> value) {
		this.closest = closest;
		this.value = value;
	}
}
