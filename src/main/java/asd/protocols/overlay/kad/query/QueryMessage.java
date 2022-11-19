package asd.protocols.overlay.kad.query;

import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

public class QueryMessage {
	public final Host destination;
	public final ProtoMessage message;

	public QueryMessage(Host destination, ProtoMessage message) {
		this.destination = destination;
		this.message = message;
	}
}
