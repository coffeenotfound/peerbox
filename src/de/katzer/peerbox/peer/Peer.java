package de.katzer.peerbox.peer;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.PeerDescriptor;
import de.katzer.peerbox.PeerDirectory;
import de.katzer.peerbox.PeerDirectory.PeerDirectoryEntry;
import de.katzer.peerbox.PeerSearchObject;
import de.katzer.peerbox.directoryserver.message.SMessageEntryResponse;
import de.katzer.peerbox.message.ConnectionHandler;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageClient;
import de.katzer.peerbox.message.MessageServer;
import de.katzer.peerbox.message.SimpleProtocol;
import de.katzer.peerbox.peer.message.CMessageEntry;
import de.katzer.peerbox.peer.message.CMessageIAmAlive;
import de.katzer.peerbox.peer.message.PMessageAreYouAlive;
import de.katzer.peerbox.peer.message.PMessageHereIsMyTime;
import de.katzer.peerbox.peer.message.PMessageHereIsYourNewTime;
import de.katzer.peerbox.peer.message.PMessageIAmFound;
import de.katzer.peerbox.peer.message.PMessageIAmLeader;
import de.katzer.peerbox.peer.message.PMessageNodeRequest;
import de.katzer.peerbox.peer.message.PMessageNodeResponse;
import de.katzer.peerbox.peer.message.PMessageNodeSearch;
import de.katzer.peerbox.peer.message.PMessageSendMsg;
import de.katzer.peerbox.peer.message.PMessageTellMeYourTime;

public class Peer {
	public static int DEFAULT_DIRECTORY_SERVER_PORT = 3333;
	public static long HEARTBEAT_INTERVAL_MS = 20_000;
	
	public static boolean USE_EMANUEL_SERVER_COMPAT_FIX = true;
	
	public InetSocketAddress directoryServerAddress = new InetSocketAddress("localhost", DEFAULT_DIRECTORY_SERVER_PORT);
	
	public boolean isServer;
	
	/** Both used for peers and for directory servers */
	protected MessageServer ourServer;
	protected MessageClient toServerClient;
	protected final Object serverClientLock = new Object();
	
	protected PeerDirectory peerDirectory;
	protected SimpleProtocol p2pProtocol;
	protected SimpleProtocol dirServerProtocol;
	
	public AtomicInteger serverNextPeerId = new AtomicInteger(0);
	
	protected IPv4 ownPeerIPv4;
	protected char ownPeerPort;
	protected char ownPeerID;
	
	public ArrayList<PeerSearchObject> searchObjectList = new ArrayList<>();
	public final Object searchObjectListLock = new Object();
	
	public int currentLeaderPeerId = -1;
	
	public Peer(boolean isServer) {
		this.isServer = isServer;
		this.peerDirectory = new PeerDirectory(this.isServer);
	}
	
	public void start() throws Exception {
		// Make the p2p protocol
		p2pProtocol = new SimpleProtocol();
		p2pProtocol.register((byte)1, CMessageEntry.class, this::handleEntryMessage);
		p2pProtocol.register((byte)2, SMessageEntryResponse.class, null); // Handled manually, thus no handler
		p2pProtocol.register((byte)3, PMessageNodeRequest.class, this::handleNodeRequest);
		p2pProtocol.register((byte)4, PMessageNodeResponse.class, this::handleNodeResponse);
		p2pProtocol.register((byte)5, CMessageIAmAlive.class, this::handleIAmAliveMessage);
		p2pProtocol.register((byte)6, PMessageNodeSearch.class, this::handleNodeSearchMessage);
		p2pProtocol.register((byte)7, PMessageIAmFound.class, this::handleIAmFoundMessage);
		p2pProtocol.register((byte)8, PMessageSendMsg.class, this::handleSendMsgMessage);
		p2pProtocol.register((byte)9, PMessageAreYouAlive.class, this::handleAreYouAliveMessage);
		p2pProtocol.register((byte)10, PMessageIAmLeader.class, this::handleIAmLeaderMessage);
		
		p2pProtocol.register((byte)11, PMessageTellMeYourTime.class, this::handleTellMeYourTimeMessage);
		p2pProtocol.register((byte)12, PMessageHereIsMyTime.class, this::handleHereIsMyTimeMessage);
		p2pProtocol.register((byte)13, PMessageHereIsYourNewTime.class, this::handleHereIsYourNewTimeMessage);
		
		// Setup message server
		int serverPort = (this.isServer ? DEFAULT_DIRECTORY_SERVER_PORT : 0);
		ourServer = new MessageServer(serverPort, new ConnectionHandler() {
			@Override
			public void handleConnection(MessageServer server, Socket remoteSocket) {
				acceptConnection(server, remoteSocket);
			}
		});
		
		try {
			// Open server socket
			ourServer.open();
		}
		catch(Exception e) {
			throw new Exception("Failed to setup message server", e);
		}
		
		// Get own ip info
		InetSocketAddress localServerAddress = ourServer.getLocalAddress();
		ownPeerIPv4 = IPv4.fromBigEndian(Inet4Address.getLocalHost().getAddress(), 0);
		ownPeerPort = (char)localServerAddress.getPort();
		ownPeerID = 0; // Zero for now, initialized when the server responds with an EntryResponseMessage
		
		// Log
		System.out.println("Started " + (this.isServer ? "directory" : "peer message") + " server at " + ownPeerIPv4 + ":" + (int)ownPeerPort);
		
//		// Make directory server protocol
//		dirServerProtocol = new SimpleProtocol();
//		dirServerProtocol.register((byte)1, CMessageEntry.class, null);
//		dirServerProtocol.register((byte)2, SMessageEntryResponse.class, null);
//		dirServerProtocol.register((byte)5, CMessageIAmAlive.class, null);
		
		if(!this.isServer) {
			// Create the client connection to the directory server
			toServerClient = new MessageClient(p2pProtocol, directoryServerAddress);
			
			// Do initial handshake
			try {
				// Create initial message client to take to the server
				try {
					toServerClient.open();
				}
				catch(Exception e) {
					throw new Exception("Failed to open handshake client", e);
				}
				
				try {
					// Send entry message
					CMessageEntry entryMessage = new CMessageEntry();
					entryMessage.peerIP = ownPeerIPv4;
					entryMessage.peerPort = ownPeerPort;
					
					// Log
					System.out.println("Sending handshake message");
					
					synchronized(serverClientLock) {
						toServerClient.sendMessage(entryMessage);
						
						// DEBUG: Compatibility fix for Emanuel's server
						// This is needed because the server always reads 8 bytes (ip + port + id) no matter what packet is send.
						// This is problematic as the EntryMsg message does not include a peer id, thus the server waits endlessly for data (and an exception is probably thrown, breaking everything)
//						if(USE_EMANUEL_SERVER_COMPAT_FIX) {
//							Socket socket = toServerClient.claimConnection();
//							socket.getOutputStream().write(new byte[2]); // Write empty id field
//							socket.getOutputStream().flush();
//						}
						
						// Receive response
						for(;;) {
							Message response = toServerClient.awaitMessage();
							
							// Log
							System.out.println("Received response...");
							
							if(response instanceof SMessageEntryResponse) {
								handleEntryResponse((SMessageEntryResponse)response);
								break;
							}
							else {
								System.err.println("Ignoring unexpected non-response packet");
							}
						}
					}
				}
				catch(Exception e) {
					throw new Exception("Failed to do initial entry handshake with server", e);
				}
				
				// Start heartbeat thread
				ScheduledExecutorService heartbeatService = Executors.newScheduledThreadPool(1);
				heartbeatService.scheduleAtFixedRate(this::sendHeartBeatToServer, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
			}
			finally {
				// Close connection again
				try {
					toServerClient.close();
				}
				catch(Exception e) {
					
				}
			}
		}
		else { // We are server
			// Generate a new peer id for ourself (directory server)
			this.ownPeerID = generateNextPeerId();
			
			// Add ourself as a peer into the peer directory
			this.peerDirectory.addPeer(ownPeerIPv4, ownPeerPort, ownPeerID, true);
			
			// Start prune task
			ScheduledExecutorService peerDirectoryPruneService = Executors.newScheduledThreadPool(1);
			peerDirectoryPruneService.scheduleAtFixedRate(this::pruneServerPeerDirectory, 31, 31, TimeUnit.SECONDS);
		}
		
		// Enter main loop
		mainLoop();
	}
	
	protected void acceptConnection(MessageServer server, Socket remoteSocket) {
		// Handle connection in seperate thread
		Thread thread = new Thread(() -> {
			// Make client instance
			MessageClient client = new MessageClient(p2pProtocol, remoteSocket);
			
			// Open the client
			client.open();
			
			// Process messages until the connection is closed
			try {
				for(;;) {
					if(client.isConnectionClosed()) break;
					
					Message message = client.awaitMessage();
					p2pProtocol.processMessage(client, message);
					
					// DEBUG: Break after the first iteration
					break;
				}
				
				// Make sure it's closed
				client.close();
			}
			catch(Exception e) {
				// Socket is done
				System.out.println("Stopping connection thread because connection is closed");
			}
		});
		thread.start();
	}
	
	/** SERVER SIDE: (1) EntryMessage */
	protected void handleEntryMessage(MessageClient client, CMessageEntry message) {
		// Log
		System.out.println("Recieved entry message from client " + message.peerIP + ":" + (int)message.peerPort);
		
		// Generate new peer id
		char newPeerId = generateNextPeerId();
		
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
		
		// Add to peer directory (important: Do this after sending the peers else we always have one missing slot that was overriden by the peer itself)
		peerDirectory.addPeer(message.peerIP, message.peerPort, newPeerId);
		
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
	
	/** CLIENT SIDE: (2) EntryMessageResponse */
	protected void handleEntryResponse(SMessageEntryResponse response) {
		// Log
		System.out.println("Server accepted entry!");
		
		// Update own id
		this.ownPeerID = response.yourId;
		System.out.println("Our id = " + (int)ownPeerID);
		
		for(int i = 0; i < response.peerIPs.length; i++) {
			String suffixString = " (invalid)";
			
			IPv4 ip = response.peerIPs[i];
			char port = response.peerPorts[i];
			char id = response.peerIds[i];
			
			if(ip.isValid() && !(ip.equals(ownPeerIPv4) && port == ownPeerPort)) { // Check if valid and not ourself
				this.peerDirectory.addPeer(ip, port, id);
				suffixString = " (id " + (int)id + ")";
			}
			System.out.println("> " + ip + ":" + (int)port + suffixString);
		}
	}
	
	/** PEER SIDE: (3) NodeRequestMessage */
	protected void handleNodeRequest(MessageClient client, PMessageNodeRequest message) {
		// Add peer to directory
		peerDirectory.addPeer(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
		
		// Send response
		final int NUM_PEERS = 4;
//		Random random = new Random();
		
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
					
					if(!(Arrays.equals(entry.ip.octetsBE, message.sourceIPv4.octetsBE) && entry.port == message.sourcePort)) {
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
		
		// Send response
		try {
			PMessageNodeResponse response = new PMessageNodeResponse(peerIPs, peerPorts, peerIds);
			client.sendMessage(response);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/** PEER SIDE: (4) NodeRequestResponse */
	protected void handleNodeResponse(MessageClient client, PMessageNodeResponse message) {
		// Log
		System.out.println("Got node response from peer! Adding to peer directory...");
		
		for(int i = 0; i < message.peerIPs.length; i++) {
			String suffixString = " (invalid)";
			
			IPv4 ip = message.peerIPs[i];
			char port = message.peerPorts[i];
			char id = message.peerIds[i];
			
			if(ip.isValid() && !(ip.equals(ownPeerIPv4) && port == ownPeerPort)) { // Check if valid and not ourself
				this.peerDirectory.addPeer(ip, port, id);
				suffixString = " (id " + (int)id + ")";
			}
			System.out.println("> " + ip + ":" + (int)port + suffixString);
		}
		
		/*
		// Add peers to directory
		for(int i = 0; i < message.peerIPs.length; i++) {
			IPv4 ip = message.peerIPs[i];
			char port = message.peerPorts[i];
			
			if(ip.isValid() && !(ip.equals(ownPeerIPv4) && port == ownPeerPort)) { // Only add if valid and not ourself
				this.peerDirectory.addPeer(ip, port, message.peerIds[i]);
			}
		}
		*/
	}
	
	/** SERVER SIDE: (5) IAmAliveMessage */
	protected void handleIAmAliveMessage(MessageClient client, CMessageIAmAlive message) {
		// Log
		System.out.println("Got hearbeat message from " + message.peerIP + ":" + (int)message.peerPort + " (id " + (int)message.peerId + ")");
		
		// Update in peer directory
		peerDirectory.addPeer(message.peerIP, message.peerPort, message.peerId);
	}
	
	/** PEER SIDE: (6) NodeSearchMessage */
	protected void handleNodeSearchMessage(MessageClient client, PMessageNodeSearch message) {
		// Add peer to directory
		peerDirectory.addPeer(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
		
//		InetSocketAddress senderAddress = client.getRemoteAddress();
		PMessageIAmFound foundMessage = null;
		
		// DEBUG: Log
		System.out.println("RECEIVED NODE SEARCH MESSAGE FOR PEER " + (int)message.destinationPeerId);
		
		// Check if we are the wanted node
		if(message.destinationPeerId == ownPeerID) {
			foundMessage = new PMessageIAmFound();
			foundMessage.sourceIPv4 = ownPeerIPv4;
			foundMessage.sourcePort = ownPeerPort;
			foundMessage.sourcePeerId = ownPeerID;
			foundMessage.searchId = message.searchId;
		}
		else {
			// Check if any of our neighbor peers is the wanted node
//			boolean isNeighbor = false;
			
//			synchronized(peerDirectory.getLock()) {
//				for(int i = 0; i < peerDirectory.size(); i++) {
//					PeerDirectoryEntry entry = peerDirectory.getEntry(i);
//					
//					if(entry.peerId == message.destinationPeerId) {
//						foundMessage = new PMessageIAmFound();
//						foundMessage.sourceIPv4 = entry.ip;
//						foundMessage.sourcePort = entry.port;
//						foundMessage.sourcePeerId = entry.peerId;
//						foundMessage.searchId = message.searchId;
//						isNeighbor = true;
//						break;
//					}
//				}
//			}
			
//			if(!isNeighbor) {
				// Forward to neighbors
				
				PeerSearchObject searchObject = null;
				boolean alreadyForwardedThisSearch = false;
				
				// Check if we have already forwarded this search
				synchronized(searchObjectListLock) {
					for(PeerSearchObject s : searchObjectList) {
						if(s != null && s.searchId == message.searchId && s.searcherPeerId == message.sourcePeerId) {
							searchObject = s;
							alreadyForwardedThisSearch = s.hasBeenForwarded;
							break;
						}
					}
				}
				
				// Only forward message if we haven't searched for this searchId yet
				if(!alreadyForwardedThisSearch) {
					// Create new search object
					if(searchObject == null) {
						synchronized(searchObjectListLock) {
//							PeerDescriptor sourcePeer = new PeerDescriptor(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
							searchObject = new PeerSearchObject(message.sourcePeerId, message.searchId);
							searchObjectList.add(searchObject);
						}
					}
					
					// Set flag
					searchObject.hasBeenForwarded = true;
					
					// Forward message to all neighbor peers (except the searcher)
					synchronized(peerDirectory.getLock()) {
						for(int i = 0; i < peerDirectory.size(); i++) {
							PeerDirectoryEntry entry = peerDirectory.getEntry(i);
							
							if(entry.peerId != message.sourcePeerId && entry.peerId != ownPeerID) {
								// DEBUG: Log
								System.out.println("FORWARDING NODE SEARCH MESSAGE TO " + entry.ip + ":" + (int)entry.port + " (id " + (int)entry.peerId + ")");
								
								try {
									sendPacketToPeerWithoutResponse(entry.ip, entry.port, message);
								}
								catch (Exception e) {
									System.err.println("Failed to forward search message");
									e.printStackTrace();
									
									// Remove from peer directory
									System.out.println("Removing peer from directory");
									peerDirectory.removeEntry(entry);
									i -= 1;
								}
							}
						}
					}
//				}
			}
		}
		
		// Send found message if we found it
		if(foundMessage != null) {
			try {
				// DEBUG:
				System.out.println("I AM FOUND, SENDING TO " + message.sourceIPv4 + ":" + (int)message.sourcePort + " (id " + (int)message.sourcePeerId + ")");
				
				// DEBUG: Close the client first
				client.close();
				
				// Send message
				sendPacketToPeerWithoutResponse(message.sourceIPv4, message.sourcePort, foundMessage);
			}
			catch(Exception e) {
				System.err.println("Failed to send IAmFound message");
				e.printStackTrace();
			}
		}
	}
	
	/** PEER SIDE: (7) IAmFoundMessage */
	protected void handleIAmFoundMessage(MessageClient client, PMessageIAmFound message) {
		boolean alreadyFoundOnce = false;
//		PeerSearchObject searchObject = null;
//		boolean forwardAgain = false;
		
		synchronized(searchObjectListLock) {
			for(PeerSearchObject s : searchObjectList) {
				if(s != null && s.searchId == message.searchId && s.searcherPeerId == ownPeerID) {
//					searchObject = s;
					alreadyFoundOnce = s.hasBeenFound;
					s.hasBeenFound = true;
				}
			}
		}
		
		if(!alreadyFoundOnce) {
			// Put the message into the promise list to be used later by the search function
			this.iAmFoundPromiseList.add(message);
		}
	}
	
	/** PEER SIDE: (8) MsgMessage */
	protected void handleSendMsgMessage(MessageClient client, PMessageSendMsg message) {
		// Add peer to directory
		peerDirectory.addPeer(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
		
		// Print to console
		System.out.println("[Message from id " + (int)message.sourcePeerId + "] " + message.messageText);
	}
	
	/** PEER SIDE: (9) AreYouAliveMessage */
	protected void handleAreYouAliveMessage(MessageClient client, PMessageAreYouAlive message) {
		// Add peer to directory
		peerDirectory.addPeer(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
		
		// Send IAmAlive response
		CMessageIAmAlive aliveResponse = new CMessageIAmAlive();
		aliveResponse.peerIP = ownPeerIPv4;
		aliveResponse.peerPort = ownPeerPort;
		aliveResponse.peerId = ownPeerID;
		
		try {
			client.sendMessage(message);
		}
		catch(Exception e) {
			System.err.println("Failed to respond with IAmAlive packet");
			e.printStackTrace();
		}
		
		// DEBUG: Close client
		try {
			client.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		// Start election
		startLeaderElection();
	}
	
	/** PEER SIDE: (10) IAmLeaderMessage */
	protected void handleIAmLeaderMessage(MessageClient client, PMessageIAmLeader message) {
		// Add peer to directory
		peerDirectory.addPeer(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
		
		// Set new leader
		this.currentLeaderPeerId = (int)message.sourcePeerId;
		
		// Log
		System.out.println("Received new leader: peer " + this.currentLeaderPeerId);
	}
	
	/** PEER SIDE: (11) TellMeYourTimeMessage */
	protected void handleTellMeYourTimeMessage(MessageClient client, PMessageTellMeYourTime message) {
		
	}
	
	/** PEER SIDE: (12) HereIsMyTimeMessage */
	protected void handleHereIsMyTimeMessage(MessageClient client, PMessageHereIsMyTime message) {
		
	}
	
	/** PEER SIDE: (13) HereIsYourNewTimeMessage */
	protected void handleHereIsYourNewTimeMessage(MessageClient client, PMessageHereIsYourNewTime message) {
		
	}
	
	protected void sendHeartBeatToServer() {
		try {
			// Log
			System.out.println("Sending heartbeat to server");
			
			// Send IAmAlive message
			Message message = new CMessageIAmAlive(this.ownPeerIPv4, this.ownPeerPort, this.ownPeerID);
			
			sendPacketToPeerWithoutResponse(IPv4.fromJavaAddress(directoryServerAddress.getAddress()), (char)directoryServerAddress.getPort(), message);
			
//			synchronized(serverClientLock) {
//				toServerClient.open();
//				toServerClient.sendMessage(message);
//				toServerClient.close();
//			}
		}
		catch(Exception e) {
			new Exception("Failed to send heartbeat to server", e);
		}
	}
	
	protected void pruneServerPeerDirectory() {
		synchronized(peerDirectory.getLock()) {
			for(int i = 0; i < peerDirectory.size(); i++) {
				PeerDirectoryEntry entry = peerDirectory.getEntry(i);
				
				if(!entry.weAreServer && entry.lastUpdateTime < System.currentTimeMillis() - 30_000L) { // 30 sec.
					peerDirectory.removeEntry(entry);
					i -= 1;
					
					// Log
					System.out.println("Pruned peer " + entry.ip + ":" + (int)entry.port + " (id " + (int)entry.peerId + ") to due inactivity");
				}
			}
		}
	}
	
	public char generateNextPeerId() {
		if(!this.isServer) {
			throw new RuntimeException("Illegal invocation: Non-server peer tried generating a new peer id");
		}
		
		int id = serverNextPeerId.getAndIncrement();
		
		if(id > (int)Character.MAX_VALUE) {
			throw new RuntimeException("Crash: Server ran out of peer ids!");
		}
		else {
			return (char)id;
		}
	}
	
	protected AtomicInteger nextSearchId = new AtomicInteger(0);
	
	protected char generateNextSearchId() {
		int id = nextSearchId.getAndIncrement();
		
		if(id > (int)Character.MAX_VALUE) {
			throw new RuntimeException("Crash: Server ran out of search ids!");
		}
		else {
			return (char)id;
		}
	}
	
	protected void sendPacketToPeerWithoutResponse(IPv4 targetIP, char targetPort, Message message) throws Exception {
		try {
			MessageClient client = new MessageClient(p2pProtocol, new InetSocketAddress(targetIP.toJavaAddress(), targetPort));
			client.open();
			
			// DEBUG: May or may not solve the 'connection refused: connect' bug when forwarding node search messages
			Thread.sleep(100L);
			
			client.sendMessage(message);
			
			client.close();
		}
		catch(Exception e) {
			throw new Exception("Failed to send message to peer", e);
		}
	}
	
	/**
	 * Used as a promise to retrieve the seperately returned IAmFoundMessage from the connection thread
	 * to the peer serach function.
	 * This is very bad but needed for compatibility with Emanuels server.
	 */
	protected BlockingDeque<PMessageIAmFound> iAmFoundPromiseList = new LinkedBlockingDeque<>();
	
	protected PeerDescriptor searchForPeerId(char targetPeerId, long timeoutMS) {
		// Search in our peer directory first
		PeerDirectory directory = this.peerDirectory;
		synchronized(directory.getLock()) {
			for(int i = 0; i < directory.size(); i++) {
				PeerDirectoryEntry entry = directory.getEntry(i);
				
				if(entry.peerId == targetPeerId) {
					return new PeerDescriptor(entry.ip, entry.port, entry.peerId);
				}
			}
		}
		
		// Generate a new search id
		char ourSearchId = generateNextSearchId();
		
		// Create search object
		PeerSearchObject searchObject = new PeerSearchObject(ownPeerID, ourSearchId);
		synchronized(this.searchObjectListLock) {
			this.searchObjectList.add(searchObject);
		}
		
		// Make message
		PMessageNodeSearch searchMessage = new PMessageNodeSearch();
		searchMessage.sourceIPv4 = ownPeerIPv4;
		searchMessage.sourcePort = ownPeerPort;
		searchMessage.sourcePeerId = ownPeerID;
		
		searchMessage.searchId = ourSearchId;
		searchMessage.destinationPeerId = targetPeerId;
		
		// Start search by sending packets to all peers in our directory
		synchronized(directory.getLock()) {
			for(int i = 0; i < directory.size(); i++) {
				PeerDirectoryEntry entry = directory.getEntry(i);
				
				if(entry.ip.isValid() && entry.peerId != ownPeerID) {
					// DEBUG:
					System.out.println("SENDING NODE SEARCH START MESSAGE TO " + entry.ip + ":" + (int)entry.port + " (id " + (int)entry.peerId + ")");
					
					// Send message to peer
					try {
						sendPacketToPeerWithoutResponse(entry.ip, entry.port, searchMessage);
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		// Wait for response
		try {
			do {
				// Wait for |timeout| ms for a response
				PMessageIAmFound message = iAmFoundPromiseList.poll(timeoutMS, TimeUnit.MILLISECONDS);
				
				// Nobody alerted us in the given timeout, assume peer doesn't exist
				if(message == null) {
					return null;
				}
				
				// Check if we gotten the right response message (based on the searchId)
				if(message.searchId == ourSearchId) {
					return new PeerDescriptor(message.sourceIPv4, message.sourcePort, message.sourcePeerId);
				}
			}
			while(!iAmFoundPromiseList.isEmpty());
		}
		catch(Exception e) {
			System.err.println("Exception while waiting for IAmFound message");
			e.printStackTrace();
		}
		return null;
	}
	
	protected void sendTextMessageToPeer(char recipientId, String messageText) {
		// Log
		System.out.println("Sending message. Searching for recipient peer... (may take up to 2 seconds)");
		
		// Search for peer
		PeerDescriptor foundPeer = searchForPeerId(recipientId, 2000L);
		
		if(foundPeer != null) {
			System.out.println("RECIPIENT PEER FOUND");
			
			// Make message
			PMessageSendMsg message = new PMessageSendMsg();
			message.sourceIPv4 = ownPeerIPv4;
			message.sourcePort = ownPeerPort;
			message.sourcePeerId = ownPeerID;
			message.messageText = messageText;
			
			// Send message
			try {
				// DEBUG:
				System.out.println("SENDING TEXT MESSAGE TO " + foundPeer.ip + ":" + (int)foundPeer.port);
				
				sendPacketToPeerWithoutResponse(foundPeer.ip, foundPeer.port, message);
			}
			catch(Exception e) {
				System.err.println("Failed to send text message to peer");
				e.printStackTrace();
			}
		}
		else {
			System.err.println("No peer found with id (" + (int)recipientId + ")");
		}
	}
	
	/** Returns {@code true} if this peer has become the new leader, {@code false} otherwise. */
	protected boolean startLeaderElection() {
		final int maxPeerId = 25;
		
		// Log
		System.out.println("Started leader election");
		
		// First check if we are the leader (have the highest peer id)
		boolean doWeHaveTheHighestId = true;
		
		HIGHER_ID_SEARCH:
		for(int i = maxPeerId; i > ownPeerID; i--) {
			// Log
			System.out.println(".. Checking peer " + i);
			
			// Search peer with id
			PeerDescriptor foundPeer = searchForPeerId((char)i, 2000L);
			
			if(foundPeer != null) {
				boolean isAlive = false;
				
				// Check if really alive
				InetSocketAddress addr = new InetSocketAddress(foundPeer.ip.toJavaAddress(), foundPeer.port);
				MessageClient client = new MessageClient(p2pProtocol, addr);
				
				PMessageAreYouAlive aliveMessage = new PMessageAreYouAlive();
				aliveMessage.sourceIPv4 = ownPeerIPv4;
				aliveMessage.sourcePort = ownPeerPort;
				aliveMessage.sourcePeerId = ownPeerID;
				
				try {
					// Try to open client
					client.open();
					
					try {
						// Send message
						client.sendMessage(aliveMessage);
						
						// Read response
						@SuppressWarnings("unused")
						Message response = client.awaitMessage();
						
						// Set flag if successful
						isAlive = true;
					}
					catch(Exception e) {
						// DEBUG:
						e.printStackTrace();
					}
				}
				catch(Exception e) {
					// Couldn't open connection
					isAlive = false;
				}
				finally {
					try {
						client.close();
					}
					catch(Exception e) {}
				}
				
				// If the peer is alive we definitely aren't the leader, so break
				if(isAlive) {
					// Log
					System.out.println(".. Found alive peer " + i);
					
					doWeHaveTheHighestId = false;
					break HIGHER_ID_SEARCH;
				}
			}
		}
		
		// If we have the highest id, we are the leader
		if(doWeHaveTheHighestId) {
			// Log
			System.out.println("Did not find a higher id so we are the new leader");
			
			// Set new leader
			this.currentLeaderPeerId = ownPeerID;
			
			// Notify all peers with lower id (faster, because there exist no with a higher id, else they would be leader)
			for(int i = ownPeerID - 1; i >= 0; i--) {
				try {
					// Log
					System.out.println(".. Notifing peer " + i);
					
					// Search peer with id
					PeerDescriptor foundPeer = searchForPeerId((char)i, 2000L);
					
					if(foundPeer != null) {
						// Send message
						PMessageIAmLeader leaderMessage = new PMessageIAmLeader();
						leaderMessage.sourceIPv4 = ownPeerIPv4;
						leaderMessage.sourcePort = ownPeerPort;
						leaderMessage.sourcePeerId = ownPeerID;
						
						sendPacketToPeerWithoutResponse(foundPeer.ip, foundPeer.port, leaderMessage);
					}
				}
				catch(Exception e) {
					System.err.println("Failed to send IAmLeader message to peer");
					e.printStackTrace();
				}
			}
			
			// We are the new leader, return true
			return true;
		}
		else {
			// Reset current leader id
			this.currentLeaderPeerId = -1;
			
			// We didn't have the highest id, someone else is the new leader: return false
			return false;
		}
	}
	
	protected void mainLoop() {
		/*
		// DEBUG: Example: Ask for nodes
		for(;;) {
			try {
				Thread.sleep(5000L);
				if(!this.isServer) {
					exampleAskForNodes();
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		*/
		
		// Make stdin reader
		try(Scanner inputScanner = new Scanner(System.in)) {
			for(;;) {
				try {
					String inputLine = inputScanner.nextLine();
					String[] splitLine = inputLine.split(" ");
					
					if(splitLine.length > 0) {
						if(splitLine[0].equals("help")) {
							System.out.println("Commands:");
							System.out.println("  msg <recipient_id> <message>    Sends the message to recipient");
							System.out.println("  leader                          Displays the current leader");
							System.out.println("  elect                           Starts a leader election");
						}
						// msg: Send message to peer with id
						else if(splitLine[0].equals("msg")) {
							// Get params
							int recipientId = Integer.parseInt(splitLine[1]);
							
							// Combine message
							String messageText = null;
							for(int i = 2; i < splitLine.length; i++) {
								if(messageText == null) {
									messageText = splitLine[i];
								}
								else {
									messageText += " " + splitLine[i];
								}
							}
							
							// Send message
							sendTextMessageToPeer((char)recipientId, messageText);
						}
						else if(splitLine[0].equals("leader")) {
							// Print current leader
							int leader = this.currentLeaderPeerId;
							System.out.println(leader < 0 ? "No leader known (hold an election first)" : ("Current leader is peer " + leader));
						}
						else if(splitLine[0].equals("peerlist")) {
							synchronized(peerDirectory.getLock()) {
								for(int i = 0; i < 4 && i < peerDirectory.size(); i++) {
									PeerDirectoryEntry entry = peerDirectory.getEntry(i);
									System.out.println("peer #" + i + ": " + entry.ip + ":" + (int)entry.port + " (id " + (int)entry.peerId + ")");
								}
							}
						}
						else if(splitLine[0].equals("elect")) {
							// Start election
							boolean weAreLeader = startLeaderElection();
							
							// Print
							if(weAreLeader) {
								System.out.println("Election finished: We are the new leader (id " + currentLeaderPeerId + ")");
							}
							else {
								System.out.println("Election finished: We are not the new leader but we should be notified soon");
							}
						}
					}
				}
				catch(Exception e) {
					System.err.println("Error while executing command!");
					e.printStackTrace();
				}
			}
		}
	}
	
	@Deprecated
	protected void exampleAskForNodes() {
		// EXAMPLE: Ask peers for nodes
		MessageClient client = null;
		IPv4 peerIP = null;
		char peerPort = 0;
		try {
			// Get random peer
			synchronized(peerDirectory.getLock()) {
				int randomIndex = new Random().nextInt(peerDirectory.size());
				
				PeerDirectoryEntry entry = peerDirectory.getEntry(randomIndex);
				peerIP = entry.ip;
				peerPort = entry.port;
			}
			
			// If we got ourselves, try again
			if(Arrays.equals(peerIP.octetsBE, Inet4Address.getLocalHost().getAddress()) && peerPort == ourServer.getLocalAddress().getPort()) {
				System.out.println("Tried to aks ourself for nodes... Trying again after wait");
				return;
			}
			if(peerIP.octetsBE[0] == 0) {
				return;
			}
			
			// Log
			System.out.println("Asking peer " + peerIP + ":" + (int)peerPort + " for nodes...");
			
			// Open client
			client = new MessageClient(p2pProtocol, new InetSocketAddress(peerIP.toJavaAddress(), (int)peerPort));
			client.open();
			
			// Ask for nodes (lol)
			PMessageNodeRequest request = new PMessageNodeRequest();
			request.sourceIPv4 = IPv4.fromBigEndian(Inet4Address.getLocalHost().getAddress(), 0);
			request.sourcePort = (char)ourServer.getLocalAddress().getPort();
			client.sendMessage(request);
			
			// Handle response
			try {
				for(;;) {
					Message response = client.awaitMessage();
					
					if(response instanceof PMessageNodeResponse) {
						p2pProtocol.processMessage(client, (PMessageNodeResponse)response);
						break;
					}
					else {
						System.err.println("Ignoring unexpected non-response packet");
					}
				}
			}
			catch(SocketException e) {
				// Log
				System.err.println("Connection was closeds");
			}
		}
		catch(SocketException e) {
			System.err.println("Peer " + peerIP + ":" + (int)peerPort + " is not available anymore. Removing them from the peer directory");
			
			// Remove from directory
//				peerDirectory.removePeerN
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			// Close client
			if(client != null) {
				try {
					client.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
//	protected void networkThreadMain() {
//		
//	}
}
