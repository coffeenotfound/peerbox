package de.katzer.peerbox.peer.message;

import java.io.IOException;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.TransceiveUtils;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageReceiveContext;
import de.katzer.peerbox.message.MessageTransmitContext;

public class PMessageNodeResponse  extends Message {
	public static final int NUM_ADDRESSES = 4;
	
	public IPv4[] peerIPs;
	public char[] peerPorts;
	public char[] peerIds;
	
	public PMessageNodeResponse() {
		// Empty constructor
	}
	
	public PMessageNodeResponse(IPv4[] peerIPs, char[] peerPorts, char[] peerIds) {
		this.peerIPs = peerIPs;
		this.peerPorts = peerPorts;
		this.peerIds = peerIds;
	}
	
	@Override
	public void receive(MessageReceiveContext context) throws IOException {
		// Allocate arrays
		peerIPs = new IPv4[NUM_ADDRESSES];
		peerPorts = new char[NUM_ADDRESSES];
		peerIds = new char[NUM_ADDRESSES];
		
		// Receive data
		byte[] receiveBuffer = context.tempBuffer(4);
		for(int i = 0; i < NUM_ADDRESSES; i++) {
			peerIPs[i] = IPv4.fromBigEndian(context.receive(4, receiveBuffer), 0);
			peerPorts[i] = TransceiveUtils.charFromBigEndian(context.receive(2, receiveBuffer), 0);
			peerIds[i] = TransceiveUtils.charFromBigEndian(context.receive(2, receiveBuffer), 0);
		}
	}
	
	@Override
	public void transmit(MessageTransmitContext context) throws IOException {
		if(peerIPs.length != NUM_ADDRESSES || peerPorts.length != NUM_ADDRESSES) throw new IllegalStateException("CMessageEntryResponse must contain exactly " + NUM_ADDRESSES + " peer addresses");
		
		// Transmit addresses
		byte[] portBuffer = context.tempBuffer(2);
		for(int i = 0; i < NUM_ADDRESSES; i++) {
			context.send(peerIPs[i].octetsBE);
			context.send(TransceiveUtils.charToBigEndian(peerPorts[i], portBuffer, 0), 0, 2);
			context.send(TransceiveUtils.charToBigEndian(peerIds[i], portBuffer, 0), 0, 2);
		}
	}
	
	@Override
	public void processMessage(boolean serverSide) {
		
	}
	
	@Override
	public byte getTag() {
		return 4;
	}
	
	@Override
	public byte getVersion() {
		return 1;
	}
	
	@Override
	public boolean hasResponse() {
		return false;
	}
}