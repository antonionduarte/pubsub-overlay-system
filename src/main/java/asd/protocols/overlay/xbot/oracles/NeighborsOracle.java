package asd.protocols.overlay.xbot.oracles;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Properties;

public class NeighborsOracle extends GenericProtocol {

	public static final short PROTOCOL_ID = 900;
	public static final String PROTOCOL_NAME = "Hyparview";

	public NeighborsOracle(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {

	}
}
