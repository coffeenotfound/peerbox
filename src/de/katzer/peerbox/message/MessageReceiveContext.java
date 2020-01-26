package de.katzer.peerbox.message;

import java.io.IOException;
import java.io.InputStream;

public interface MessageReceiveContext {
	public byte[] receive(int numBytes, byte[] data) throws IOException;
	public byte[] tempBuffer(int minSize);
	
	public static class StreamReceiveContext implements MessageReceiveContext {
		protected byte[] cachedTempBuffer = new byte[8];
		
		protected InputStream stream;
		
		public StreamReceiveContext(InputStream stream) {
			this.stream = stream;
		}
		
		@Override
		public byte[] receive(int numBytes, byte[] data) throws IOException {
			InputStream readStream = this.stream;
			
			int readBytes = 0;
			while(readBytes < numBytes) {
				int n = readStream.read(data, readBytes, numBytes-readBytes);
				
				if(n == -1) {
					throw new IOException("End of socket inputstream reached");
				}
				else {
					readBytes += n;
				}
			}
			return data;
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
