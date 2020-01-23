package de.katzer.peerbox.message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import de.katzer.peerbox.message.MessageTransmitContext.StreamTransmitContext;
import de.katzer.peerbox.message.MessageReceiveContext.StreamReceiveContext;

public class MessageClient {
	protected Protocol protocol;
	protected InetSocketAddress serverAddress;
	
	protected final Object socketLock = new Object();
	protected Socket cachedSocket = null;
	protected Thread receiveThread = null;
	
	protected BlockingDeque<Message> receiveQueue = new LinkedBlockingDeque<Message>();
	protected LinkedList<Thread> waitingThreads = new LinkedList<>();
	protected final Object waitingThreadListLock = new Object();
	
	protected AtomicBoolean opened = new AtomicBoolean(false);
	protected AtomicBoolean closed = new AtomicBoolean(false);
	
	/** Creates a new local client */
	public MessageClient(Protocol protocol, InetSocketAddress serverAddress) {
		this.protocol = protocol;
		this.serverAddress = serverAddress;
	}
	
	/** Creates a new remote client */
	public MessageClient(Protocol protocol, Socket socket) {
		this.protocol = protocol;
		this.cachedSocket = socket;
		this.serverAddress = (InetSocketAddress)socket.getLocalSocketAddress();
	}
	
	public InetSocketAddress getRemoteAddress() {
		return this.serverAddress;
	}
	
	public void open() {
		if(opened.compareAndSet(false, true)) {
			// Create receive thread
//			this.receiveThread = new Thread(() -> receiveThreadMain(), "message receive thread");
//			this.receiveThread.start();
			
			// DEBUG:
			try {
				claimConnection();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void close() throws IOException {
		if(closed.compareAndSet(true, false)) {
			// Interrupt receive thread
			this.receiveThread.interrupt();
			
			// Close socket
			synchronized(socketLock) {
				if(cachedSocket != null) {
					this.cachedSocket.close();
					this.cachedSocket = null;
				}
			}
		}
	}
	
	public Socket claimConnection() throws IOException {
		synchronized(socketLock) {
			if(cachedSocket == null || cachedSocket.isClosed()) {
				// Open new connection
				return this.cachedSocket = new Socket(this.serverAddress.getAddress(), this.serverAddress.getPort());
			}
			else {
				return this.cachedSocket;
			}
		}
	}
	
	protected void receiveThreadMain() {
		try {
			for(;;) {
				// If the socket is closed, stop
				if(this.isConnectionClosed()) {
					return;
				}
				
				// Wait for a message
				try {
					tryReceiveMessage(claimConnection());
				}
				catch(SocketException e) { // When connection reset
					// Log
					System.out.println("Socket reset...");
					throw e;
				}
				catch(Exception e) {
					throw e;
				}
			}
		}
		catch(Exception e) {
			// Log
			System.out.println("Exception while trying to receiving a message");
			e.printStackTrace();
			
			// Set closed
			this.closed.set(true);
			
			// Wake up waiting threads
			synchronized(waitingThreads) {
				for(Thread thread : waitingThreads) {
					if(thread != null) {
						thread.interrupt();
					}
				}
			}
		}
	}
	
	protected void tryReceiveMessage(Socket socket) throws Exception {
		try {
			// Make receive context
			MessageReceiveContext receiveContext = new StreamReceiveContext(socket.getInputStream());
			
			// Read message header
			byte[] messageHeaderBuffer = receiveContext.tempBuffer(2);
			receiveContext.receive(2, messageHeaderBuffer);
			
			// Decode message header
			byte tag = messageHeaderBuffer[0];
			byte version = messageHeaderBuffer[1];
			
			// Let protocol make message
			Message message = protocol.makeMessageContainer(tag, version);
			
			if(message != null) {
				// Receive message
				message.receive(receiveContext);
				
				// Put message into receive queue
				receiveQueue.offer(message);
			}
			else {
				System.err.println("Unknown message with tag " + tag + ", version " + version);
			}
		}
		catch(Exception e) {
			throw e;
		}
	}
	
	public void sendMessage(Message message) throws IOException {
		try {
			// Get connection socket
			Socket socket = this.claimConnection();
			
			// Make context
			MessageTransmitContext context = new StreamTransmitContext(socket.getOutputStream());
			
			// Send header
			byte[] headerBuffer = context.tempBuffer(1);
			
			headerBuffer[0] = message.tag;
			context.send(headerBuffer, 0, 1);
			
			headerBuffer[0] = message.version;
			context.send(headerBuffer, 0, 1);
			
			// Send message
			message.transmit(context);
		}
		catch(Exception e) {
			throw e;
		}
	}
	
	public Message awaitMessage() throws SocketException, Exception {
		if(this.isConnectionClosed()) throw new SocketException("Connection closed");
		
		try {
			Socket connection = claimConnection();
			
			tryReceiveMessage(connection);
			
			return receiveQueue.take();
		}
		catch(Exception e) {
			throw new Exception("DEBUG: Failed to read message", e);
		}
	}
	
	/*
	public Message awaitMessage() throws SocketException {
		for(;;) {
			if(this.isConnectionClosed()) throw new SocketException("Connection closed");
			
			try {
				Thread currentThread = Thread.currentThread();
				synchronized(waitingThreadListLock) {
					waitingThreads.add(currentThread);
				}
				
				Message message = receiveQueue.take();
				
				synchronized(waitingThreadListLock) {
					waitingThreads.remove(currentThread);
				}
				
				return message;
			}
			catch(InterruptedException e) {}
		}
	}
	*/
	
	public boolean isConnectionClosed() {
		return (cachedSocket != null && cachedSocket.isClosed()) || this.closed.get();
	}
}
