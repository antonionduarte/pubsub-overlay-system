package asd.protocols.overlay.kad.bcast;

import java.util.List;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;

/**
 * Tracks the state of the BroadcastHave's.
 * When we receive a BroadcastMessage we then sent Have messages to a set of
 * peers.
 * The set of peers contains peers from a left bucket and a right side.
 * The left bucket could be `depth`(the depth of the message that increments
 * after every node that receives it) and the right side is all the buckets
 * after the left bucket.
 * This set of peer is sampled from the routing table so we dont have a
 * guarantee that we will
 * end up broadcasting messages to all peers in the overlay. To prevent this
 * from happening every peer that receives a message should make progress in
 * broadcasting it to the overlay.
 * To achieve this we initially send a BroadcastHave message to that set of
 * peers but if none of them send us a BroadcastWant message after a certain
 * amount of time then we will send a BroadcastHave message to one or more new
 * peers. We only track peers on the right side. The left bucket is ignored
 * since it only takes one of them to receive the message to start making
 * progress on the other side of the address space.
 * We continue this until there are no more new peers.
 * 
 */
public class BroadcastTracker {
    public static record ExpiredBroadcast(UUID uuid) {
    }

    public BroadcastTracker() {
    }

    /**
     * Start tracking a new message. This should be called when we receive a
     * BroadcastMessage and are about to send the BroadcastHave's.
     * After starting to track a message we should call `sentWant` to the peer that
     * sent us the message and to all the peers in the right side.
     * 
     * @param uuid
     * @param depth
     */
    public void startTracking(UUID uuid, int depth) {

    }

    /**
     * Stop tracking a message. This should be called when we don't have any new
     * peers to send to.
     * 
     * @param uuid
     */
    public void stopTracking(UUID uuid) {

    }

    /**
     * Register that we sent a BroadcastHave message to a peer.
     * Should only be called on right side peers.
     * 
     * @param uuid
     * @param peer_id
     */
    public void sentWant(UUID uuid, KadID peer_id) {

    }

    /**
     * Register that we received a BroadcastWant message from a peer.
     * This will stop tracking the message since we have already made progress.
     * This can be called with any peer since if it is on the left bucket then we
     * know what since we never called `sentWant` on it.
     * 
     * @param uuid
     * @param peer_id
     */
    public void receiveWant(UUID uuid, KadID peer_id) {
    }

    /**
     * Checks if we have already sent a BroadcastHave message to a peer.
     * This is used to pick the next peers to send the BroadcastHave message to.
     * 
     * @param uuid
     * @param peer_id
     */
    public void isValidSendPeer(UUID uuid, KadID peer_id) {
    }

    public List<ExpiredBroadcast> checkTimeouts() {
        return null;
    }
}
