package asd;

import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.ipc.FindClosest;
import asd.protocols.overlay.kad.ipc.FindClosestReply;
import asd.protocols.overlay.kad.ipc.FindSwarm;
import asd.protocols.overlay.kad.ipc.FindSwarmReply;
import asd.protocols.overlay.kad.ipc.JoinSwarm;
import asd.protocols.overlay.kad.ipc.JoinSwarmReply;
import asd.protocols.overlay.kad.ipc.StoreValue;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

public class InteractiveKad extends GenericProtocol {
    public static final short ID = 1000;
    public static final String NAME = "Interactive Kademlia";

    private final Kademlia kad;

    public InteractiveKad(Kademlia kad) throws HandlerRegistrationException {
        super(NAME, ID);
        this.kad = kad;

        registerReplyHandler(FindClosestReply.ID, this::onFindClosestReply);
        registerReplyHandler(FindSwarmReply.ID, this::onFindSwarmReply);
        registerReplyHandler(JoinSwarmReply.ID, this::onJoinSwarmReply);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try (var scanner = new Scanner(System.in)) {
                    while (true) {
                        var line = scanner.nextLine();
                        var components = line.split(" ");

                        switch (components[0]) {
                            case "exit" -> System.exit(0);
                            case "self" -> {
                                System.out.println(kad.getID());
                            }
                            case "store" -> {
                                var key_str = components[1];
                                var key = KadID.ofData(key_str.getBytes());
                                var value_str = components[2];
                                var value = value_str.getBytes();
                                sendRequest(new StoreValue(key, value), Kademlia.ID);
                            }
                            case "closest" -> {
                                var cpl = Integer.parseInt(components[1]);
                                var target = KadID.randomWithCpl(kad.getID(), cpl);
                                System.out.println("Finding closest with cpl = " + cpl);
                                sendRequest(new FindClosest(target), Kademlia.ID);
                            }
                            case "find" -> {
                                var key = KadID.ofData(components[1]);
                                sendRequest(new FindClosest(key), Kademlia.ID);
                            }
                            case "sjoin" -> {
                                sendRequest(new JoinSwarm(KadID.ofData(components[1])), Kademlia.ID);
                            }
                            case "sfind" -> {
                                sendRequest(new FindSwarm(KadID.ofData(components[1])), Kademlia.ID);
                            }
                            case "rt" -> kad.printRoutingTable();
                            default -> System.out.println("Unknown command " + components[0]);
                        }
                    }
                }
            }

        }).start();
    }

    private void onFindClosestReply(FindClosestReply reply, short source_proto) {
        System.out.println("Got reply with closest nodes:");
        var self = this.kad.getID();
        for (var peer : reply.closest) {
            System.out.println(" - " + peer + " (cpl = " + self.cpl(peer.id) + ")");
        }
    }

    private void onFindSwarmReply(FindSwarmReply reply, short source_proto) {
        System.out.println("Got reply with swarm members:");
        var self = this.kad.getID();
        for (var peer : reply.peers) {
            System.out.println(" - " + peer + " (cpl = " + self.cpl(peer.id) + ")");
        }
    }

    private void onJoinSwarmReply(JoinSwarmReply reply, short source_proto) {
        System.out.println("Joined swarm, got reply with swarm members:");
        var self = this.kad.getID();
        for (var peer : reply.peers) {
            System.out.println(" - " + peer + " (cpl = " + self.cpl(peer.id) + ")");
        }
    }

}
