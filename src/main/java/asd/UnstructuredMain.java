package asd;

import asd.metrics.Metrics;
import asd.protocols.apps.AutomatedApp;
import asd.protocols.apps.InteractiveApp;
import asd.protocols.dissemination.plumtree.PlumTree;
import asd.protocols.overlay.hyparview.Hyparview;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.protocols.pubsub.unstructured.UnstructuredPubsub;
import asd.utils.InterfaceToIp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.network.data.Host;

import java.net.InetAddress;
import java.util.Properties;

public class UnstructuredMain {

	// Sets the log4j (logging library) configuration file
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
	}

	// Creates the logger object
	private static final Logger logger = LogManager.getLogger(ManualMain.class);

	// Default babel configuration file (can be overridden by the "-config" launch
	// argument)
	private static final String DEFAULT_CONF = "babel_config.properties";

	// Numerical identifier of the pub-sub protocol to be used by the application
	private static final short PUBSUB_PROTO_ID = UnstructuredPubsub.PROTO_ID;

	public static void main(String[] args) throws Exception {

		// Get the (singleton) babel instance
		Babel babel = Babel.getInstance();

		// Loads properties from the configuration file, and merges them with properties
		// passed in the launch arguments
		Properties props = Babel.loadConfig(args, DEFAULT_CONF);

		// If you pass an interface name in the properties (either file or arguments),
		// this wil get the IP of that interface
		// and create a property "address=ip" to be used later by the channels.
		InterfaceToIp.addInterfaceIp(props);

		Metrics.initMetrics(Integer.parseInt(props.getProperty("babel_port")));

		// The Host object is an address/port pair that represents a network host. It is
		// used extensively in babel
		// It implements equals and hashCode, and also includes a serializer that makes
		// it easy to use in network messages
		Host myself = new Host(InetAddress.getByName(props.getProperty("babel_address")),
				Integer.parseInt(props.getProperty("babel_port")));

		logger.info("Hello, I am {}", myself);

		GenericProtocol app = null;
		if (props.getProperty("automated").equals("true")) {
			app = new AutomatedApp(myself, props, PUBSUB_PROTO_ID);
		} else {
			app = new InteractiveApp(myself, props, PUBSUB_PROTO_ID);
		}

		var hyparview = new Hyparview(props, myself);

		// Dissemination protocol
		var plumtree = new PlumTree(props, myself);

		// PubSub protocol
		var pubsub = new UnstructuredPubsub();

		// Register applications in babel
		babel.registerProtocol(app);

		babel.registerProtocol(hyparview);

		// Register protocols
		babel.registerProtocol(plumtree);

		babel.registerProtocol(pubsub);

		// Init the protocols. This should be done after creating all protocols, since
		// there can be inter-protocol
		// communications in this step.
		hyparview.init(props);
		plumtree.init(props);
		pubsub.init(props);
		app.init(props);

		// Start babel and protocol threads
		babel.start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.info("Goodbye")));

	}
}