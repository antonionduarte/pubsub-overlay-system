package asd.protocols.overlay.kad;

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
	public String toString() {
		var builder = new StringBuilder();
		for (int i = 0; i < KadID.ID_LENGTH; ++i) {
			if (i != 0)
				builder.append(", ");
			builder.append(String.format("%03d", this.distance[i] & 0xFF));
		}
		return builder.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		if (other == this)
			return true;
		if (!(other instanceof KadDistance))
			return false;
		return this.compareTo((KadDistance) other) == 0;
	}

	@Override
	public int compareTo(KadDistance o) {
		for (int i = 0; i < KadID.ID_LENGTH; ++i) {
			var diff = (this.distance[i] & 0xFF) - (o.distance[i] & 0xFF);
			if (diff != 0)
				return diff;
		}
		return 0;
	}
}
