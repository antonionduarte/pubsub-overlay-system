package asd;

import asd.protocols.overlay.hyparview.Hyparview;
import asd.utils.InterfaceToIp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;
import java.util.Properties;

public class HyparviewMain {

	// Creates the logger object
	private static final Logger logger = LogManager.getLogger(Hyparview.class);
	// Default babel configuration file (can be overridden by the "-config" launch argument)
	private static final String DEFAULT_CONF = "babel_config.properties";

	// Sets the log4j (logging library) configuration file
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
	}

	public static void main(String[] args) {
		try {
			// Get the (singleton) babel instance
			Babel babel = Babel.getInstance();

			// Loads properties from the configuration file, and merges them with properties passed in the launch arguments
			Properties props = Babel.loadConfig(args, DEFAULT_CONF);

			// If you pass an interface name in the properties (either file or arguments), this wil get the IP of that interface
			// and create a property "address=ip" to be used later by the channels.
			InterfaceToIp.addInterfaceIp(props);

			// The Host object is an address/port pair that represents a network host. It is used extensively in babel
			// It implements equals and hashCode, and also includes a serializer that makes it easy to use in network messages
			Host myself = new Host(InetAddress.getByName(props.getProperty("babel_address")), Integer.parseInt(props.getProperty("babel_port")));

			logger.info("Hello, I am {}", myself);

			Hyparview hyparview = new Hyparview(props, myself);

			babel.registerProtocol(hyparview);

			hyparview.init(props);

			// Start babel and protocol threads
			babel.start();
		} catch (Exception e) {
			logger.error("Error starting babel", e);
		}
	}
}
