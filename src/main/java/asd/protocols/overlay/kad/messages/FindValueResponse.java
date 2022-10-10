package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindValueResponse extends ProtoMessage {
    public static final short ID = Kademlia.ID + 4;

    public final Optional<byte[]> value;
    public final List<KadPeer> closest;

    public FindValueResponse(List<KadPeer> closest) {
        this(closest, Optional.empty());
    }

    public FindValueResponse(List<KadPeer> closest, byte[] value) {
        this(closest, Optional.of(value));
    }

    public FindValueResponse(List<KadPeer> closest, Optional<byte[]> value) {
        super(ID);
        this.closest = closest;
        this.value = value;
    }

    @Override
    public String toString() {
        return "FindValueResponse{" +
                "value=" + value +
                ", closest=" + closest +
                '}';
    }

    public static final ISerializer<FindValueResponse> serializer = new ISerializer<FindValueResponse>() {
        @Override
        public void serialize(FindValueResponse t, ByteBuf out) throws IOException {
            out.writeInt(t.closest.size());
            for (KadPeer peer : t.closest) {
                KadPeer.serializer.serialize(peer, out);
            }
            out.writeBoolean(t.value.isPresent());
            if (t.value.isPresent()) {
                out.writeInt(t.value.get().length);
                out.writeBytes(t.value.get());
            }
        }

        @Override
        public FindValueResponse deserialize(ByteBuf in) throws IOException {
            var closest = new ArrayList<KadPeer>(in.readInt());
            for (int i = 0; i < closest.size(); i++) {
                closest.add(KadPeer.serializer.deserialize(in));
            }
            if (in.readBoolean()) {
                byte[] value = new byte[in.readInt()];
                in.readBytes(value);
                return new FindValueResponse(closest, value);
            } else {
                return new FindValueResponse(closest);
            }
        }
    };
}
