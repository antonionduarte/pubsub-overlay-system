package asd;

import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.ipc.StoreValue;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

public class InteractiveKad extends GenericProtocol {
    public static final short ID = 1000;
    public static final String NAME = "Interactive Kademlia";

    private final Kademlia kad;

    public InteractiveKad(Kademlia kad) {
        super(NAME, ID);
        this.kad = kad;
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
                            case "exit":
                                System.exit(0);
                                break;
                            case "store":
                                var key_str = components[1];
                                var key = KadID.ofData(key_str.getBytes());
                                var value_str = components[2];
                                var value = value_str.getBytes();
                                sendRequest(new StoreValue(key, value), Kademlia.ID);
                                break;
                            case "rt":
                                kad.printRoutingTable();
                                break;
                            default:
                                System.out.println("Unknown command " + components[0]);
                                break;
                        }
                    }
                }
            }

        }).start();
    }

}
