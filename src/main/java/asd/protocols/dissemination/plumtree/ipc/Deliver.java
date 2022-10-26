package asd.protocols.dissemination.plumtree.ipc;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class Deliver extends ProtoReply {

	public static final short REPLY_ID = 100;

	private final byte[] msg;

	public Deliver(byte[] msg) {
		super(REPLY_ID);
		this.msg = msg;
	}

	public byte[] getMsg() {
		return msg;
	}
}
