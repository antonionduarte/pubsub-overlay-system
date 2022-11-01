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

	public static void messageReceived(UUID messageId) {
		writeMetric(new MessageReceived(messageId.toString()), "messageReceived");
	}

	public record Metric(long timestamp, String type, Object message) {}

	public record MessageReceived(String messageId) {}


}
