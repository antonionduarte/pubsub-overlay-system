package asd.protocols.dissemination.plumtree.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class Broadcast extends ProtoRequest {

	public static final short ID = 100;

	public Broadcast() {
		super(ID);
	}
}
