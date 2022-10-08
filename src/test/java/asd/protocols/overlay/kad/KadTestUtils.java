package asd.protocols.overlay.kad;

import java.net.Inet4Address;
import java.util.concurrent.ThreadLocalRandom;

import pt.unl.fct.di.novasys.network.data.Host;

public class KadTestUtils {
	public static KadPeer randomPeer() {
		var id = KadID.random();
		var port = ThreadLocalRandom.current().nextInt() % 0xFFFF;
		var host = new Host(Inet4Address.getLoopbackAddress(), port);
		return new KadPeer(id, host);
	}
}
