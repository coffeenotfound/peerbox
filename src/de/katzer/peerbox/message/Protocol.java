package de.katzer.peerbox.message;

public interface Protocol {
	public Message makeMessageContainer(byte tag, byte version);
	public void processMessage(MessageClient client, Message message);
}
