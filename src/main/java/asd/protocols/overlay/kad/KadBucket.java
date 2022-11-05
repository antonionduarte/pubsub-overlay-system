package asd.protocols.overlay.kad;

import java.util.Iterator;

public class KadBucket implements Iterable<KadPeer> {
	private final KadPeer[] peers;
	private int size;

	public KadBucket(int k) {
		this.peers = new KadPeer[k];
		this.size = 0;
	}

	public boolean add(KadPeer peer) {
		if (this.isFull())
			return false;
		var index = this.findPeerIndex(peer.id);
		if (index != -1)
			return false;
		this.peers[this.size] = peer;
		this.size += 1;
		return true;
	}

	public boolean contains(KadID id) {
		return this.findPeerIndex(id) != -1;
	}

	/**
	 * Perform a swap-remove on `index`.
	 * 
	 * @param index Index of the peer to remove
	 * @return The peer at `index`
	 */
	public KadPeer remove(int index) {
		if (index >= this.size)
			throw new IndexOutOfBoundsException();
		var peer = this.peers[index];
		this.peers[index] = this.peers[this.size - 1];
		this.peers[this.size - 1] = null;
		this.size -= 1;
		return peer;
	}

	public boolean removeByID(KadID id) {
		var index = this.findPeerIndex(id);
		if (index == -1)
			return false;
		this.remove(index);
		return true;
	}

	public KadPeer get(int index) {
		if (index >= this.size)
			throw new IndexOutOfBoundsException();
		return this.peers[index];
	}

	public int size() {
		return this.size;
	}

	public boolean isFull() {
		return this.size == this.peers.length;
	}

	private int findPeerIndex(KadID id) {
		for (int i = 0; i < this.size; ++i)
			if (this.peers[i].id.equals(id))
				return i;
		return -1;
	}

	@Override
	public Iterator<KadPeer> iterator() {
		return new Iterator<KadPeer>() {
			private int index = 0;

			@Override
			public boolean hasNext() {
				return this.index < KadBucket.this.size;
			}

			@Override
			public KadPeer next() {
				var peer = KadBucket.this.peers[this.index];
				this.index += 1;
				return peer;
			}
		};
	}
}
