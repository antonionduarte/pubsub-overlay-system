package asd.protocols.overlay.kad;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

class KadUtils {
	static int byteLeadingZeroes(byte v) {
		var lz = 0;
		for (int j = 7; j >= 0; --j) {
			var b = v & (1 << j);
			if (b == 0)
				lz += 1;
			else
				break;
		}
		return lz;
	}

	static KadPeer[] messageReadPeerList(ByteBuf buf) {
		var n = buf.readShort();
		var peers = new KadPeer[n];

		return peers;
	}

	static void messageWritePeerList(KadPeer[] peers, ByteBuf buf) throws IOException {
		buf.writeShort(peers.length);
		for (var peer : peers)
			KadPeer.serializer.serialize(peer, buf);
	}
}
