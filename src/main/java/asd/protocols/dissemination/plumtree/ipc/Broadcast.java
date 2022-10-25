package asd.protocols.dissemination.plumtree.ipc;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class Broadcast extends ProtoRequest {

	public static final short ID = 100;

	private byte[] msg;

	public Broadcast(byte[] msg) {
		super(ID);
		this.msg = msg;
	}

	public byte[] getMsg() {
		return msg;
	}
}
