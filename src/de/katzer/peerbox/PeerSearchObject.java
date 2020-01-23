package de.katzer.peerbox;

public class PeerSearchObject {
//	public PeerDescriptor sourcePeer;
	public char searcherPeerId;
	public char searchId;
	public boolean hasBeenForwarded = false;
	public boolean hasBeenFound = false;
	
	public PeerSearchObject(char searcherPeerId, char searchId) {
		this.searcherPeerId = searcherPeerId;
		this.searchId = searchId;
//		this.sourcePeer = sourcePeer;
		this.searchId = searchId;
	}
}
