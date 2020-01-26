package de.katzer.peerbox.message;

import java.io.IOException;
import java.io.OutputStream;

public interface MessageTransmitContext {
	public byte[] tempBuffer(int minSize);
	
	public void send(byte[] data, int start, int length) throws IOException;
	
	public default void send(byte[] data) throws IOException {
		this.send(data, 0, data.length);
	}
	
	public static class StreamTransmitContext implements MessageTransmitContext {
		protected OutputStream stream;
		
		protected byte[] cachedTempBuffer = new byte[8];
		
		public StreamTransmitContext(OutputStream stream) {
			this.stream = stream;
		}
		
		@Override
		public void send(byte[] data, int start, int length) throws IOException {
			OutputStream writeStream = this.stream;
			writeStream.write(data, start, length);
		}
		
		@Override
		public byte[] tempBuffer(int minSize) {
			if(cachedTempBuffer.length < minSize) {
				cachedTempBuffer = new byte[minSize];
			}
			return cachedTempBuffer;
		}
	}
}
