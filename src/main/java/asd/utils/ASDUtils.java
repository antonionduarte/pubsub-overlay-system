package asd.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
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

	public static ISerializer<String> stringSerializer = new ISerializer<String>() {
		@Override
		public void serialize(String s, ByteBuf byteBuf) throws IOException {
			var len = s.getBytes().length;
			byteBuf.writeInt(len);
			ByteBufUtil.reserveAndWriteUtf8(byteBuf, s, len);
		}

		@Override
		public String deserialize(ByteBuf byteBuf) throws IOException {
			var len = byteBuf.readInt();
			return byteBuf.readCharSequence(len, StandardCharsets.UTF_8).toString();
		}
	};
}
