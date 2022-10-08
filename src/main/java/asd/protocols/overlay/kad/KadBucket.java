package asd.protocols.overlay.kad;

public class KadBucket {
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
}
