package de.katzer.peerbox.start;

import de.katzer.peerbox.peer.Peer;

public class StartServer {
	public static void main(String[] args) {
		try {
			new Peer(true).start();
		}
		catch(Exception e) {
			System.err.println("Uncaught exception in server");
			e.printStackTrace();
		}
	}
}
