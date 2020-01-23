package de.katzer.peerbox.start;

import de.katzer.peerbox.peer.Peer;

public class StartPeer {
	public static void main(String[] args) {
		try {
			new Peer(false).start();
		}
		catch(Exception e) {
			System.err.println("Uncaught exception in peer");
			e.printStackTrace();
		}
	}
}
