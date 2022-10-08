package asd.protocols.overlay.kad;

import java.util.Objects;

public class KadDistance implements Comparable<KadDistance> {
	private final byte[] distance;

	KadDistance(byte[] distance) {
		if (distance.length != KadID.ID_LENGTH)
			throw new IllegalArgumentException("KadDistance must be " + KadID.ID_LENGTH + " bytes long");
		this.distance = distance;
	}

	public boolean isZero() {
		for (int i = 0; i < KadID.ID_LENGTH; ++i)
			if (this.distance[i] != 0)
				return false;
		return true;
	}

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		if (other == this)
			return true;
		if (!(other instanceof KadDistance))
			return false;
		return Objects.equals(this.distance, ((KadDistance) other).distance);
	}

	@Override
	public int compareTo(KadDistance o) {
		for (int i = 0; i < KadID.ID_LENGTH; ++i) {
			if (this.distance[i] < o.distance[i])
				return -1;
			if (this.distance[i] > o.distance[i])
				return 1;
		}
		return 0;
	}
}
