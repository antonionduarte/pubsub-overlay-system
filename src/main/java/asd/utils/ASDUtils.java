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

	public static <T> Set<T> sample(int size, Set<T> set) {
		List<T> list = new ArrayList<>(set);
		Set<T> subset = new HashSet<>();
		Collections.shuffle(list);
		for (int i = 0; i < Math.min(size, list.size()); i++) {
			subset.add(list.get(i));
		}
		return subset;
	}
}
