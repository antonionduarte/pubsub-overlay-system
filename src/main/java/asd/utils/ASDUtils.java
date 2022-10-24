package asd.utils;

import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;
import java.util.*;

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

	public static Set<Host> peerSample(int size, Set<Host> set) {
		List<Host> list = new ArrayList<>(set);
		Set<Host> subset = new HashSet<>();
		Collections.shuffle(list);
		for (int i = 0; i < size; i++) {
			subset.add(list.get(i));
		}
		return subset;
	}



}
