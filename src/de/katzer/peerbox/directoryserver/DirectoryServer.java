package de.katzer.peerbox.directoryserver;

import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.PeerDirectory;
import de.katzer.peerbox.PeerDirectory.PeerDirectoryEntry;
import de.katzer.peerbox.directoryserver.message.SMessageEntryResponse;
import de.katzer.peerbox.message.ConnectionHandler;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageClient;
import de.katzer.peerbox.message.MessageServer;
import de.katzer.peerbox.message.SimpleProtocol;
import de.katzer.peerbox.peer.Peer;
import de.katzer.peerbox.peer.message.CMessageEntry;
import de.katzer.peerbox.peer.message.CMessageIAmAlive;

@Deprecated
public class DirectoryServer {
	public static final int DEFAULT_DIRECTORY_SERVER_PORT = 3333;
	
	public SimpleProtocol directoryServerProtocol;
	
	public PeerDirectory peerDirectory = new PeerDirectory();
	
	public AtomicInteger nextPeerId = new AtomicInteger(0);
	
	public void start() throws Exception {
		// Make protocol
		directoryServerProtocol = new SimpleProtocol();
		directoryServerProtocol.register((byte)1, CMessageEntry.class, this::handleEntryMessage);
		directoryServerProtocol.register((byte)2, SMessageEntryResponse.class, null);
		directoryServerProtocol.register((byte)5, CMessageIAmAlive.class, this::handleIAmAliveMessage);
		
		// Make message server
		MessageServer server = new MessageServer(DEFAULT_DIRECTORY_SERVER_PORT, new ConnectionHandler() {
			@Override
			public void handleConnection(MessageServer server, Socket remoteSocket) {
				System.out.println("New client connection from " + remoteSocket.getInetAddress());
				acceptConnection(server, remoteSocket);
			}
		});
		server.open();
		
		// Log
		System.out.println("Starting server at " + InetAddress.getLocalHost() + ":" + server.getLocalAddress().getPort());
		
		// Start prune task
		ScheduledExecutorService heartbeatService = Executors.newScheduledThreadPool(1);
		heartbeatService.scheduleAtFixedRate(this::prunePeers, 30, 30, TimeUnit.SECONDS);
	}
	
	protected void prunePeers() {
		synchronized(peerDirectory.getLock()) {
			for(int i = 0; i < peerDirectory.size(); i++) {
				PeerDirectoryEntry entry = peerDirectory.getEntry(i);
				
				if(entry.lastUpdateTime < System.currentTimeMillis() - 30_000L) { // 30 sec.
					peerDirectory.removeEntry(entry);
					i -= 1;
					
					// Log
					System.out.println("Pruning peer " + entry.ip + " to due inactivity");
				}
			}
		}
	}
	
	protected void handleEntryMessage(MessageClient client, CMessageEntry message) {
		// Log
		System.out.println("Recieved entry message from client " + message.peerIP + ":" + (int)message.peerPort);
		
		// Generate new peer id
		char newPeerId = generateNextPeerId();
		
		// Add to peer directory
		peerDirectory.addPeer(message.peerIP, message.peerPort, newPeerId);
		
		// Send response
		final int NUM_PEERS = 4;
		IPv4[] peerIPs = new IPv4[NUM_PEERS];
		char[] peerPorts = new char[NUM_PEERS];
		char[] peerIds = new char[NUM_PEERS];
		
		// Find random peers
		synchronized(peerDirectory.getLock()) {
			for(int i = 0; i < peerIPs.length; i++) {
//				PeerDirectoryEntry entry = peerDirectory.getEntry(random.nextInt(peerDirectory.size()));
				boolean valid = false;
				
				if(i < peerDirectory.size()) {
					PeerDirectoryEntry entry = peerDirectory.getEntry(i);
					
					if(!(Arrays.equals(entry.ip.octetsBE, message.peerIP.octetsBE) && entry.port == message.peerPort)) { // Don't send the requesting peer's info to itself
						peerIPs[i] = entry.ip;
						peerPorts[i] = entry.port;
						peerIds[i] = entry.peerId;
						valid = true;
					}
				}
				
				if(!valid) {
					peerIPs[i] = new IPv4(new byte[4]);
					peerPorts[i] = 0;
					peerIds[i] = 0;
				}
			}
		}
		
		// Log
		System.out.println("Sending entry response to client " + message.peerIP);
		
		try {
			// Send response
			SMessageEntryResponse response = new SMessageEntryResponse(peerIPs, peerPorts, peerIds, newPeerId);
			client.sendMessage(response);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void handleIAmAliveMessage(MessageClient client, CMessageIAmAlive message) {
		// DEBUG:
		System.out.println("Got hearbeat message from " + message.peerIP + ":" + (int)message.peerPort);
		
		// Update in peer directory
		peerDirectory.addPeer(message.peerIP, message.peerPort, message.peerId, false);
	}
	
	protected void acceptConnection(MessageServer server, Socket remoteSocket) {
		// Handle client connection in seperate thread
		Thread thread = new Thread(() -> {
			// Make client instance
			MessageClient client = new MessageClient(directoryServerProtocol, remoteSocket);
			client.open();
			
			// Process messages until the connection is closed
			try {
				for(;;) {
					if(client.isConnectionClosed()) return;
					
					Message message = client.awaitMessage();
					directoryServerProtocol.processMessage(client, message);
				}
			}
			catch(SocketException e) {
				// Socket is done
				System.out.println("Stopping connection thread because connection is closed");
			}
		});
		thread.start();
	}
	
	public char generateNextPeerId() {
		int id = nextPeerId.getAndIncrement();
		
		if(id > (int)Character.MAX_VALUE) {
			throw new RuntimeException("Crash: Server ran out of peer ids!");
		}
		else {
			return (char)id;
		}
	}
}
