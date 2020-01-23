package de.katzer.peerbox.message;

import java.io.IOException;

public abstract class Message {
	public byte tag = getTag();
	public byte version = getVersion();
	
	public abstract void receive(MessageReceiveContext context) throws IOException;
	public abstract void transmit(MessageTransmitContext context) throws IOException;
	
	public abstract void processMessage(boolean serverSide);
	
	public abstract byte getTag();
	public abstract byte getVersion();
	public abstract boolean hasResponse();
}
