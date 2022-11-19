package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

import java.util.List;

public class FindClosestQueryResult {
	public final List<KadID> closest;

	public FindClosestQueryResult(List<KadID> closest) {
		this.closest = closest;
	}
}
