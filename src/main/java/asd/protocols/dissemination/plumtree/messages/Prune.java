package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Prune extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 2;

	public Prune() {
		super(MSG_ID);
	}
}
