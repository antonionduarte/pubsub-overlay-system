package asd.utils;

import java.net.InetAddress;

import pt.unl.fct.di.novasys.network.data.Host;

public class ASDUtils {
	public static Host hostFromProp(String value) {
		String[] hostElems = value.split(":");
		Host host;
		try {
			host = new Host(InetAddress.getByName(hostElems[0]), Short.parseShort(hostElems[1]));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
		return host;
	}

}
