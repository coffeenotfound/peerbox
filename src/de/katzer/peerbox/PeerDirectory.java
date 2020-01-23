package de.katzer.peerbox;

import java.util.ArrayList;
import java.util.Arrays;

public class PeerDirectory {
	protected final Object lock = new Object();
	
	protected ArrayList<PeerDirectoryEntry> list = new ArrayList<>();
	protected boolean isServer;
	protected boolean unlimitedCapacityServerSide = false;
	
	public PeerDirectory(boolean isServer) {
		this.isServer = isServer;
	}
	
	public PeerDirectoryEntry addPeer(IPv4 ip, char port, char peerId) {
		return this.addPeer(ip, port, peerId, false);
	}
	
	public PeerDirectoryEntry addPeer(IPv4 ip, char port, char peerId, boolean weAreServer) {
		synchronized(lock) {
			// Check for duplicates
			PeerDirectoryEntry newEntry = null;
			
			for(PeerDirectoryEntry entry : list) {
				if(entry != null && entry.ip.equals(ip) && entry.port == port && entry.peerId == peerId) {
					newEntry = entry;
					
					// If peer already exists, update last update time
					entry.lastUpdateTime = System.currentTimeMillis();
				}
			}
			
			final int MAX_NUM_PEERS = 4;
			if(newEntry == null) {
				// If directory is at capacity, delete first peer from list (except on server side)
				if((!isServer || !unlimitedCapacityServerSide) && list.size() >= MAX_NUM_PEERS) {
					list.remove(0);
				}
				
				// Add new peer
				newEntry = new PeerDirectoryEntry(ip, port, peerId, System.currentTimeMillis(), weAreServer);
				list.add(newEntry);
			}
			return newEntry;
		}
	}
	
	public boolean removeEntry(PeerDirectoryEntry entry) {
		synchronized(lock) {
			for(int i = 0; i < list.size(); i++) {
				if(list.get(i) == entry) {
					list.remove(i);
					return true;
				}
			}
			return false;
		}
	}
	
	public boolean removePeer(IPv4 ip, char port) {
		synchronized(lock) {
			for(int i = 0; i < list.size(); i++) {
				PeerDirectoryEntry entry = list.get(i);
				
				if(entry != null && Arrays.equals(entry.ip.octetsBE, ip.octetsBE) && entry.port == port) {
					list.remove(i);
					return true;
				}
			}
			return false;
		}
	}
	
	public int size() {
		synchronized(lock) {
			return list.size();
		}
	}
	
	public PeerDirectoryEntry getEntry(int index) {
		synchronized(lock) {
			return list.get(index);
		}
	}
	
	public Object getLock() {
		return lock;
	}
	
	public static class PeerDirectoryEntry {
		public final IPv4 ip;
		public final char port;
		public final char peerId;
		/** Used so that on the directory server side we don't delete ourself (the directory server) from our own peer list. */
		public final boolean weAreServer;
		public long lastUpdateTime;
		
		public PeerDirectoryEntry(IPv4 ip, char port, char peerId, long currentTime, boolean weAreServer) {
			this.ip = ip;
			this.port = port;
			this.peerId = peerId;
			this.weAreServer = weAreServer;
			this.lastUpdateTime = currentTime;
		}
	}
}
