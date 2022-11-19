package asd.metrics;

import asd.protocols.overlay.kad.KadID;
import com.google.gson.Gson;
import pt.unl.fct.di.novasys.channel.tcp.events.ChannelMetrics;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Metrics {
	public static final int METRIC_LEVEL_OFF = 0;
	public static final int METRIC_LEVEL_BASIC = 1;
	public static final int METRIC_LEVEL_DETAILED = 2;
	private static final String FILE_PATH_MASK = "%s%d.json";
	private static final Clock clock = Clock.systemUTC();
	private static final Gson gson = new Gson();
	private static String FOLDER = "metrics/";
	private static FileOutputStream fileOutputStream;
	private static File metricsFile = null;
	private static int metricsLevel = 0;

	public static void initMetrics(Properties props) {
		var nodeId = Integer.parseInt(props.getProperty("babel_port"));

		if (props.containsKey("metrics_folder")) {
			FOLDER = props.getProperty("metrics_folder");
		}

		var filepath = String.format(FILE_PATH_MASK, FOLDER, nodeId);
		metricsFile = new File(filepath);

		metricsLevel = Integer.parseInt(props.getProperty("metrics_level"));

		try {
			Files.createDirectories(Paths.get(FOLDER));
			metricsFile.createNewFile();
			fileOutputStream = new FileOutputStream(metricsFile);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static int level() {
		return metricsLevel;
	}

	public static synchronized <T> void writeMetric(T message, String message_type) {
		if (fileOutputStream == null) {
			return;
		}

		var now = clock.instant();
		var nano_now = now.getEpochSecond() * 1_000_000_000 + ((long) now.getNano());
		var metric = new Metric(nano_now, message_type, message);
		var json = gson.toJson(metric) + "\n";

		try {
			fileOutputStream.write(json.getBytes());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void boot() {
		if (metricsLevel >= METRIC_LEVEL_BASIC) {
			writeMetric(new Boot(), "Boot");
		}
	}

	public static void shutdown() {
		if (metricsLevel >= METRIC_LEVEL_BASIC) {
			writeMetric(new Shutdown(), "Shutdown");
		}
	}

	public static void pubMessageSent(Host source, UUID messageId, String topic, boolean delivered) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new PubMessageSent(source.toString(), messageId.toString(), topic, delivered), "PubSubMessageSent");
	}

	public static void pubMessageReceived(Host source, UUID messageId, String topic, int hopCount, boolean delivered) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new PubMessageReceived(source.toString(), messageId.toString(), topic, hopCount, delivered),
				"PubSubMessageReceived");
	}

	public static void subscribedTopic(String topic) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new SubscribedTopic(topic), "PubSubSubscription");
	}

	public static void unsubscribedTopic(String topic) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new UnsubscribedTopic(topic), "PubSubUnsubscription");
	}

	public static void span(String name, double duration) {
		if (metricsLevel < METRIC_LEVEL_DETAILED) {
			return;
		}
		writeMetric(new Span(name, duration), "Span");
	}

	public static void network(long in, long out) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new Network(in, out), "Network");
	}

	public static void network(ChannelMetrics event) {
		long in = 0;
		long out = 0;
		var conns = List.of(event.getInConnections(), event.getOldInConnections(), event.getOutConnections(),
				event.getOldOutConnections());
		for (var connl : conns) {
			for (var conn : connl) {
				in += conn.getReceivedAppBytes();
				out += conn.getSentAppBytes();
			}
		}
		network(in, out);
	}

	public static void messageSent(Host destination, MetricsMessage msg) {
		if (metricsLevel < METRIC_LEVEL_DETAILED) {
			return;
		}
		if (msg == null) {
			return;
		}
		writeMetric(new MessageSent(msg.name, destination.toString(), msg.properties), "MessageSent");
	}

	public static void messageSent(Host destination, MetricsProtoMessage msg) {

		messageSent(destination, msg.serializeToMetric());
	}

	public static void messageReceived(Host source, MetricsMessage msg) {
		if (metricsLevel < METRIC_LEVEL_DETAILED) {
			return;
		}
		if (msg == null) {
			return;
		}
		writeMetric(new MessageReceived(msg.name, source.toString(), msg.properties), "MessageReceived");
	}

	public static void messageReceived(Host source, MetricsProtoMessage msg) {
		messageReceived(source, msg.serializeToMetric());
	}

	public static void kademliaIdentifier(KadID id) {
		if (metricsLevel < METRIC_LEVEL_BASIC) {
			return;
		}
		writeMetric(new KademliaIdentifier(id.toString()), "KademliaIdentifier");
	}

	public static void routingTable(String topic, List<List<String>> routingTable) {
		if (metricsLevel < METRIC_LEVEL_DETAILED) {
			return;
		}
		writeMetric(new RoutingTable(topic, routingTable), "RoutingTable");
	}

	public record Metric(long timestamp, String metric_type, Object metric) {
	}

	public record Boot() {
	}

	public record Shutdown() {
	}

	public record PubMessageSent(String source, String message_id, String topic, boolean delivered) {
	}

	public record PubMessageReceived(String source, String message_id, String topic, int hop_count, boolean delivered) {
	}

	public record SubscribedTopic(String topic) {
	}

	public record UnsubscribedTopic(String topic) {
	}

	public record Span(String name, double duration) {
	}

	public record Network(long inbound, long outbound) {
	}

	public record MessageSent(String message_type, String destination, Map<String, Object> properties) {
	}

	public record MessageReceived(String message_type, String source, Map<String, Object> properties) {
	}

	public record KademliaIdentifier(String identifier) {
	}

	public record RoutingTable(String topic, List<List<String>> buckets) {

	}

}
