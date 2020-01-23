package de.katzer.peerbox.message;

@FunctionalInterface
public interface MessageHandler<M extends Message> {
	public void handle(MessageClient client, M message);
}
