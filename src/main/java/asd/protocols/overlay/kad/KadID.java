package asd.protocols.overlay.kad;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.hash.Hashing;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class KadID {
	public static final int ID_LENGTH = 20;

	private final byte[] id;

	/**
	 * @param id 20 byte long identifier
	 * @throws IllegalArgumentException If the id is not the correct lenght
	 */
	public KadID(byte[] id) {
		if (id.length != ID_LENGTH)
			throw new IllegalArgumentException("KadID must be " + ID_LENGTH + " bytes long");
		this.id = Arrays.copyOf(id, ID_LENGTH);
	}

	public KadDistance distanceTo(KadID other) {
		var distance = new byte[ID_LENGTH];
		for (int i = 0; i < ID_LENGTH; ++i)
			distance[i] = (byte) (this.id[i] ^ other.id[i]);
		return new KadDistance(distance);
	}

	public int cpl(KadID other) {
		int cpl = 0;
		for (int i = 0; i < ID_LENGTH; ++i) {
			var xor = (byte) (this.id[i] ^ other.id[i]);
			var lz = KadUtils.byteLeadingZeroes(xor);
			cpl += lz;
			if (lz != 8)
				break;
		}
		return cpl;
	}

	@Override
	public String toString() {
		return HexFormat.of().formatHex(this.id);
	}

	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof KadID))
			return false;
		if (other == this)
			return true;
		var other_id = (KadID) other;
		return Arrays.equals(this.id, other_id.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

	static KadID create(int... id) {
		var b = new byte[ID_LENGTH];
		for (int i = 0; i < id.length; ++i) {
			if (id[i] > 255)
				throw new IllegalArgumentException("integer out of bounds for byte");
			b[i] = (byte) id[i];
		}
		return new KadID(b);
	}

	public static KadID zero() {
		return new KadID(new byte[ID_LENGTH]);
	}

	public static KadID random() {
		var id = new byte[ID_LENGTH];
		ThreadLocalRandom.current().nextBytes(id);
		return new KadID(id);
	}

	public static KadID ofData(byte[] data) {
		try {
			return KadID.ofData(new ByteArrayInputStream(data));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("unreachable");
		}
	}

	public static KadID ofData(InputStream stream) throws IOException {
		var buffer = new byte[4096];
		@SuppressWarnings("deprecation")
		var hasher = Hashing.sha1().newHasher();
		while (true) {
			var n = stream.read(buffer);
			hasher.putBytes(buffer, 0, n);
			if (n != buffer.length)
				break;
		}
		var hash = hasher.hash().asBytes();
		return new KadID(hash);
	}

	public static final ISerializer<KadID> serializer = new ISerializer<KadID>() {
		@Override
		public KadID deserialize(ByteBuf buf) throws IOException {
			var id = new byte[ID_LENGTH];
			buf.readBytes(id);
			return new KadID(id);
		}

		@Override
		public void serialize(KadID id, ByteBuf buf) throws IOException {
			buf.writeBytes(id.id);
		}
	};
}
