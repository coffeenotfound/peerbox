package de.katzer.peerbox.peer.message;

import java.io.IOException;
import de.katzer.peerbox.IPv4;
import de.katzer.peerbox.TransceiveUtils;
import de.katzer.peerbox.message.Message;
import de.katzer.peerbox.message.MessageReceiveContext;
import de.katzer.peerbox.message.MessageTransmitContext;
import de.katzer.peerbox.peer.Peer;

public class CMessageEntry extends Message {
	public IPv4 peerIP;
	public char peerPort;
	
	@Override
	public void receive(MessageReceiveContext context) throws IOException {
		// Receive ipv4
		this.peerIP = IPv4.fromBigEndian(context.receive(4, new byte[4]), 0);
		
		// Retrieve port
		this.peerPort = TransceiveUtils.charFromBigEndian(context.receive(2, context.tempBuffer(2)), 0);
	}
	
	@Override
	public void transmit(MessageTransmitContext context) throws IOException {
		if(Peer.USE_EMANUEL_SERVER_COMPAT_FIX) {
			byte[] msg = new byte[6];
			
			System.arraycopy(peerIP.octetsBE, 0, msg, 0, 4);
			System.arraycopy(TransceiveUtils.charToBigEndian(peerPort, context.tempBuffer(2), 0), 0, msg, 4, 2);
			
//			// DEBUG:
//			System.out.println("SENDING ENTRY MESSAGE: " + Arrays.toString(msg));
			
			context.send(msg);
		}
		else {
			// Transmit ipv4
			context.send(peerIP.octetsBE);
			
			// Transmit port
			context.send(TransceiveUtils.charToBigEndian(peerPort, context.tempBuffer(2), 0), 0, 2);
		}
	}
	
	@Override
	public void processMessage(boolean serverSide) {
		
	}
	
	@Override
	public byte getTag() {
		return 1;
	}
	
	@Override
	public byte getVersion() {
		return 0;
	}
	
	@Override
	public boolean hasResponse() {
		return true;
	}
}
