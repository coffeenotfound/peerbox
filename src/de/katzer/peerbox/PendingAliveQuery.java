package de.katzer.peerbox;

public class PendingAliveQuery {
	public char targetPeerId;
	public long queryTime;
	
	public PendingAliveQuery(char targetPeerId, long queryTime) {
		this.targetPeerId = targetPeerId;
		this.queryTime = queryTime;
	}
}
