package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

import java.util.List;

public class FindPoolQueryResult {
	public final List<KadID> closest;
	public final List<KadID> members;

	public FindPoolQueryResult(List<KadID> closest, List<KadID> members) {
		this.closest = closest;
		this.members = members;
	}
}
