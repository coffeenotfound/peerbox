package de.katzer.peerbox.peer.message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.TransceiveUtils;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageReceiveContext;
import de.katzer.peerbox.message.MessageTransmitContext;

public class PMessageSendMsg extends Message {
	public IPv4 sourceIPv4;
	public char sourcePort;
	public char sourcePeerId;
	
	public String messageText;
	
	@Override
	public void receive(MessageReceiveContext context) throws IOException {
		// Receive ipv4
		this.sourceIPv4 = IPv4.fromBigEndian(context.receive(4, new byte[4]), 0);
		
		// Retrieve port
		this.sourcePort = TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
		
		// Retrieve id
		this.sourcePeerId = TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
		
		// Receive message
		int messageLength = (int)TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
		
		byte[] messageBuffer = new byte[messageLength];
		context.receive(messageLength, messageBuffer);
		
		this.messageText = new String(messageBuffer, StandardCharsets.UTF_8);
	}
	
	@Override
	public void transmit(MessageTransmitContext context) throws IOException {
		// Transmit ipv4
		context.send(this.sourceIPv4.octetsBE);
		
		// Transmit port
		context.send(TransceiveUtils.charToBigEndian(this.sourcePort, context.tempBuffer(2), 0), 0, 2);
		
		// Transmit id
		context.send(TransceiveUtils.charToBigEndian(this.sourcePeerId, context.tempBuffer(2), 0), 0, 2);
		
		// Transmit message
		byte[] messageBuffer = this.messageText.getBytes(StandardCharsets.UTF_8);
		char messageLength = (char)messageBuffer.length;
		
		context.send(TransceiveUtils.charToBigEndian(messageLength, context.tempBuffer(2), 0), 0, 2);
		context.send(messageBuffer);
	}
	
	@Override
	public void processMessage(boolean serverSide) {
		
	}
	
	@Override
	public byte getTag() {
		return 8;
	}
	
	@Override
	public byte getVersion() {
		return 1;
	}
	
	@Override
	public boolean hasResponse() {
		return true;
	}
}
