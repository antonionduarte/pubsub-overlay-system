package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class Graft extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 3;

	public Graft() {
		super(MSG_ID);
	}
}
