package de.katzer.peerbox.message;

public class SimpleProtocol implements Protocol {
//	protected ArrayList<RegistryEntry<? extends Message>> registry = new ArrayList<>();
	@SuppressWarnings("unchecked")
	protected RegistryEntry<? extends Message>[] registry = new RegistryEntry[20];
	
	public <M extends Message> void register(byte tag, Class<M> messageClass, MessageHandler<M> handler) {
//		registry.ensureCapacity((tag & 0xFF) + 1);
//		registry.set(tag & 0xFF, new RegistryEntry<M>(messageClass, (MessageHandler<M>)handler));
		registry[tag & 0xFF] = new RegistryEntry<M>(messageClass, (MessageHandler<M>)handler);
	}
	
	protected RegistryEntry<? extends Message> lookupRegisteredMessage(byte tag, byte version) {
//		RegistryEntry<? extends Message> entry = registry.get(tag & 0xFF);
		RegistryEntry<? extends Message> entry = registry[tag & 0xFF];
		return entry;
	}
	
	@Override
	public Message makeMessageContainer(byte tag, byte version) {
		RegistryEntry<? extends Message> entry = lookupRegisteredMessage(tag, version);
		
		if(entry != null) {
			try {
				return entry.messageClass.newInstance();
			}
			catch(Exception e) {
				throw new RuntimeException("Failed to instantiate message", e);
			}
		}
		else {
			throw new IllegalStateException("Unknown message with tag=" + tag + ", version=" + version);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void processMessage(MessageClient client, Message message) {
		RegistryEntry<? extends Message> entry = lookupRegisteredMessage(message.tag, message.version);
		
		if(entry != null && entry.handler != null) {
			((MessageHandler<Message>)entry.handler).handle(client, message);
		}
	}
	
	public static class RegistryEntry<M extends Message> {
		public Class<M> messageClass;
		public MessageHandler<M> handler;
		
		public RegistryEntry(Class<M> messageClass, MessageHandler<M> handler) {
			this.messageClass = messageClass;
			this.handler = handler;
		}
	}
}
