package de.katzer.peerbox.message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageServer {
	protected int port;
	
	protected AtomicBoolean opened = new AtomicBoolean(false);
	
	protected ServerSocket serverSocket;
	protected Thread acceptThread;
	protected ConnectionHandler connectionHandler;
	
	public MessageServer(int port, ConnectionHandler connectionHandler) {
		this.port = port;
		this.connectionHandler = connectionHandler;
	}
	
	public void open() throws IOException {
		if(opened.compareAndSet(false, true)) {
			// Create and bind the server socket
			this.serverSocket = new ServerSocket(this.port);
			
			// Create and start the accept thread
			acceptThread = new Thread(() -> this.acceptThreadMain(), "peer accept thread");
			acceptThread.start();
		}
	}
	
	protected void acceptThreadMain() {
		for(;;) {
			try {
				// Wait to accept connection
				Socket remoteSocket = this.serverSocket.accept();
				
				// Call connection handler
				connectionHandler.handleConnection(this, remoteSocket);
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public InetSocketAddress getLocalAddress() {
		return (InetSocketAddress)serverSocket.getLocalSocketAddress();
	}
	
	public boolean isOpen() {
		return opened.get();
	}
}
