package de.katzer.peerbox.message;

import java.net.Socket;

public interface ConnectionHandler {
	public void handleConnection(MessageServer server, Socket remoteSocket);
}
