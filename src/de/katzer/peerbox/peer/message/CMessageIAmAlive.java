package de.katzer.peerbox.peer.message;

import java.io.IOException;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.TransceiveUtils;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageReceiveContext;
import de.katzer.peerbox.message.MessageTransmitContext;

public class CMessageIAmAlive extends Message {
	public IPv4 peerIP;
	public char peerPort;
	public char peerId;
	
	public CMessageIAmAlive() {
		// Empty constructor
	}
	
	public CMessageIAmAlive(IPv4 ip, char port, char peerId) {
		this.peerIP = ip;
		this.peerPort = port;
		this.peerId = peerId;
	}
	
	@Override
	public void receive(MessageReceiveContext context) throws IOException {
		// Receive ipv4
		this.peerIP = IPv4.fromBigEndian(context.receive(4, new byte[4]), 0);
		
		// Retrieve port
		this.peerPort = TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
		
		// Retrieve id
		this.peerId = TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
	}
	
	@Override
	public void transmit(MessageTransmitContext context) throws IOException {
		// Transmit ipv4
		context.send(peerIP.octetsBE);
		
		// Transmit port
		context.send(TransceiveUtils.charToBigEndian(peerPort, context.tempBuffer(2), 0), 0, 2);
		
		// Transmit id
		context.send(TransceiveUtils.charToBigEndian(peerId, context.tempBuffer(2), 0), 0, 2);
	}
	
	@Override
	public void processMessage(boolean serverSide) {
		
	}
	
	@Override
	public byte getTag() {
		return 5;
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
