package asd.protocols.overlay.hyparview.ipc;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class GetPeers extends ProtoRequest {

	public static final short ID = 100;

	public GetPeers() {
		super(ID);
	}
}
