package asd.metrics;

import java.util.HashMap;
import java.util.Map;

public class MetricsMessage {
	final String name;
	final Map<String, Object> properties;

	public MetricsMessage(String name) {
		this.name = name;
		this.properties = new HashMap<>();
	}

	public MetricsMessage property(String name, Object value) {
		properties.put(name, value);
		return this;
	}
}
