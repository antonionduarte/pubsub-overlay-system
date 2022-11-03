package asd.metrics;

import com.google.gson.Gson;

import java.io.*;
import java.util.UUID;

public class Metrics {

	public static final String FILE_PATH = "metrics/metrics_%d.json";
	public static FileOutputStream fileOutputStream;
	public static File metricsFile = null;

	private static final Gson gson = new Gson();

	public static void initMetrics(int nodeId)  {
		metricsFile = new File(String.format(FILE_PATH, nodeId));

		try {
			metricsFile.createNewFile();
			fileOutputStream = new FileOutputStream(metricsFile);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static synchronized <T> void writeMetric(T message, String messageType) {
		var metric = new Metric(System.currentTimeMillis(), messageType, message);
		var json = gson.toJson(metric) + "\n";

		try {
			fileOutputStream.write(json.getBytes());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void messageReceivedHops(UUID messageId, String topic, int hopCount) {
		writeMetric(new MessageReceivedHops(messageId.toString(), topic, hopCount), "messageReceived");
	}

	public static void pubMessageSent(UUID messageId, String topic, boolean delivered) {
		writeMetric(new PubMessageSent(messageId.toString(), topic, delivered), "pubSent");
	}

	public static void pubMessageReceived(UUID messageId, String topic, int hopCount, boolean delivered) {
		writeMetric(new PubMessageReceived(messageId.toString(), topic, hopCount, delivered), "pubReceived");
	}

	public static void subscribedTopic(String topic) {
		writeMetric(new SubscribedTopic(topic), "subscribedTopic");
	}

	public static void unsubscribedTopic(String topic) {
		writeMetric(new UnsubscribedTopic(topic), "unsubscribedTopic");
	}

	public record Metric(long timestamp, String type, Object message) {}

	public record SubscribedTopic(String topic) {}

	public record UnsubscribedTopic(String topic) {}

	public record MessageReceivedHops(String messageId, String topic, int hopCount) {}

	public record PubMessageSent(String messageId, String topic, boolean delivered) {}

	public record PubMessageReceived(String messageId, String topic, int hopCount, boolean delivered) {}
}
