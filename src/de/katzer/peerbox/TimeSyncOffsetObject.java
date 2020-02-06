package de.katzer.peerbox;

public class TimeSyncOffsetObject {
	public PeerDescriptor senderPeer;
	public long offsetMS;
	
	public TimeSyncOffsetObject(PeerDescriptor senderPeer, long offsetMS) {
		this.senderPeer = senderPeer;
		this.offsetMS = offsetMS;
	}
}
