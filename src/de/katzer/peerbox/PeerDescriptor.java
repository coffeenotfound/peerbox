package de.katzer.peerbox;

public class PeerDescriptor {
	public IPv4 ip;
	public char port;
	public char peerId;
	
	public PeerDescriptor(IPv4 ip, char port, char peerId) {
		this.ip = ip;
		this.port = port;
		this.peerId = peerId;
	}
}
