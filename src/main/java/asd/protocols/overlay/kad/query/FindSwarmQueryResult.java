package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

import java.util.List;

public class FindSwarmQueryResult {
	public final List<KadID> closest;
	public final List<KadID> members;

	public FindSwarmQueryResult(List<KadID> closest, List<KadID> members) {
		this.closest = closest;
		this.members = members;
	}
}
