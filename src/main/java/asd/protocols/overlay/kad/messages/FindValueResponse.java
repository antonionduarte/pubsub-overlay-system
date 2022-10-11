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

    public final int context;
    public final Optional<byte[]> value;
    public final List<KadPeer> peers;

    public FindValueResponse(int context, List<KadPeer> closest) {
        this(context, closest, Optional.empty());
    }

    public FindValueResponse(int context, List<KadPeer> closest, byte[] value) {
        this(context, closest, Optional.of(value));
    }

    public FindValueResponse(int context, List<KadPeer> closest, Optional<byte[]> value) {
        super(ID);
        this.context = context;
        this.peers = closest;
        this.value = value;
    }

    @Override
    public String toString() {
        return "FindValueResponse{" +
                "context=" + context +
                ", value=" + value +
                ", closest=" + peers +
                '}';
    }

    public static final ISerializer<FindValueResponse> serializer = new ISerializer<FindValueResponse>() {
        @Override
        public void serialize(FindValueResponse t, ByteBuf out) throws IOException {
            out.writeInt(t.context);
            out.writeBoolean(t.value.isPresent());
            if (t.value.isPresent()) {
                out.writeInt(t.value.get().length);
                out.writeBytes(t.value.get());
            }
            out.writeInt(t.peers.size());
            for (KadPeer peer : t.peers) {
                KadPeer.serializer.serialize(peer, out);
            }
        }

        @Override
        public FindValueResponse deserialize(ByteBuf in) throws IOException {
            var context = in.readInt();
            var hasValue = in.readBoolean();
            Optional<byte[]> value = Optional.empty();
            if (hasValue) {
                var valueLength = in.readInt();
                var valueBytes = new byte[valueLength];
                in.readBytes(valueBytes);
                value = Optional.of(valueBytes);
            }
            var size = in.readInt();
            var closest = new ArrayList<KadPeer>(size);
            for (int i = 0; i < size; i++) {
                closest.add(KadPeer.serializer.deserialize(in));
            }
            return new FindValueResponse(context, closest, value);
        }
    };
}
