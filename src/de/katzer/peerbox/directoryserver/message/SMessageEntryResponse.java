package de.katzer.peerbox.directoryserver.message;

import java.io.IOException;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.TransceiveUtils;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageReceiveContext;
import de.katzer.peerbox.message.MessageTransmitContext;

public class SMessageEntryResponse extends Message {
	public static final int NUM_ADDRESSES = 4;
	
	public char yourId;
	public IPv4[] peerIPs;
	public char[] peerPorts;
	public char[] peerIds;
	
	public SMessageEntryResponse() {
		// Empty constructor
	}
	
	public SMessageEntryResponse(IPv4[] peerIPs, char[] peerPorts, char[] peerIds, char yourId) {
		this.peerIPs = peerIPs;
		this.peerPorts = peerPorts;
		this.peerIds = peerIds;
		this.yourId = yourId;
	}
	
	@Override
	public void receive(MessageReceiveContext context) throws IOException {
		// Allocate arrays
		peerIPs = new IPv4[NUM_ADDRESSES];
		peerPorts = new char[NUM_ADDRESSES];
		peerIds = new char[NUM_ADDRESSES];
		
		// Receive data
		byte[] receiveBuffer = context.tempBuffer(4);
		
		yourId = TransceiveUtils.charFromBigEndian(context.receive(2, receiveBuffer), 0);
		
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
		byte[] charBuffer = context.tempBuffer(2);
		
		context.send(TransceiveUtils.charToBigEndian(yourId, charBuffer, 0), 0, 2);
		
		for(int i = 0; i < NUM_ADDRESSES; i++) {
			context.send(peerIPs[i].octetsBE);
			context.send(TransceiveUtils.charToBigEndian(peerPorts[i], charBuffer, 0), 0, 2);
			context.send(TransceiveUtils.charToBigEndian(peerIds[i], charBuffer, 0), 0, 2);
		}
	}
	
	@Override
	public void processMessage(boolean serverSide) {
		
	}
	
	@Override
	public byte getTag() {
		return 2;
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
