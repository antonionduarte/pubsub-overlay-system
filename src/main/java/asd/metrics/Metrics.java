package asd.metrics;

import com.google.gson.Gson;

import pt.unl.fct.di.novasys.network.data.Host;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class Metrics {
	private static final int METRIC_LEVEL_OFF = 0;
	private static final int METRIC_LEVEL_BASIC = 1;
	private static final int METRIC_LEVEL_DETAILED = 2;

	private static String FOLDER = "metrics/";
	private static final String FILE_PATH_MASK = "%s%d.json";
	private static FileOutputStream fileOutputStream;
	private static File metricsFile = null;
	private static int metricsLevel = 0;

	private static final long startTime = System.nanoTime();
	private static final Gson gson = new Gson();

	public static void initMetrics(Properties props) {
		var nodeId = Integer.parseInt(props.getProperty("babel_port"));

		if (props.containsKey("metrics_folder"))
			FOLDER = props.getProperty("metrics_folder");

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

	public record Metric(long timestamp, String metric_type, Object metric) {
	}

	public static synchronized <T> void writeMetric(T message, String message_type) {
		if (fileOutputStream == null)
			return;

		var metric = new Metric(System.nanoTime() - startTime, message_type, message);
		var json = gson.toJson(metric) + "\n";

		try {
			fileOutputStream.write(json.getBytes());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public record PubMessageSent(String source, String message_id, String topic, boolean delivered) {
	}

	public static void pubMessageSent(Host source, UUID messageId, String topic, boolean delivered) {
		if (metricsLevel < METRIC_LEVEL_BASIC)
			return;
		writeMetric(new PubMessageSent(source.toString(), messageId.toString(), topic, delivered), "PubSubMessageSent");
	}

	public record PubMessageReceived(String source, String message_id, String topic, int hop_count, boolean delivered) {
	}

	public static void pubMessageReceived(Host source, UUID messageId, String topic, int hopCount, boolean delivered) {
		if (metricsLevel < METRIC_LEVEL_BASIC)
			return;
		writeMetric(new PubMessageReceived(source.toString(), messageId.toString(), topic, hopCount, delivered),
				"PubSubMessageReceived");
	}

	public record SubscribedTopic(String topic) {
	}

	public static void subscribedTopic(String topic) {
		if (metricsLevel < METRIC_LEVEL_BASIC)
			return;
		writeMetric(new SubscribedTopic(topic), "PubSubSubscription");
	}

	public record UnsubscribedTopic(String topic) {
	}

	public static void unsubscribedTopic(String topic) {
		if (metricsLevel < METRIC_LEVEL_BASIC)
			return;
		writeMetric(new UnsubscribedTopic(topic), "PubSubUnsubscription");
	}

	public record Span(String name, double duration) {
	}

	public static void span(String name, double duration) {
		if (metricsLevel < METRIC_LEVEL_DETAILED)
			return;
		writeMetric(new Span(name, duration), "Span");
	}

	public record Network(long inbound, long outbound) {
	}

	public static void network(long in, long out) {
		if (metricsLevel < METRIC_LEVEL_BASIC)
			return;
		writeMetric(new Network(in, out), "Network");
	}

	public record MessageSent(String message_type, String destination, Map<String, Object> properties) {
	}

	public static void messageSent(Host destination, MetricsMessage msg) {
		if (metricsLevel < METRIC_LEVEL_DETAILED)
			return;
		if (msg == null)
			return;
		writeMetric(new MessageSent(msg.name, destination.toString(), msg.properties), "MessageSent");
	}

	public static void messageSent(Host destination, MetricsProtoMessage msg) {

		messageSent(destination, msg.serializeToMetric());
	}

	public record MessageReceived(String message_type, String source, Map<String, Object> properties) {
	}

	public static void messageReceived(Host source, MetricsMessage msg) {
		if (metricsLevel < METRIC_LEVEL_DETAILED)
			return;
		if (msg == null)
			return;
		writeMetric(new MessageReceived(msg.name, source.toString(), msg.properties), "MessageReceived");
	}

	public static void messageReceived(Host source, MetricsProtoMessage msg) {
		messageReceived(source, msg.serializeToMetric());
	}

	public record RoutingTable(String topic, List<List<String>> buckets) {

	}

	public static void routingTable(String topic, List<List<String>> routingTable) {
		if (metricsLevel < METRIC_LEVEL_DETAILED)
			return;
		writeMetric(new RoutingTable(topic, routingTable), "RoutingTable");
	}

}
