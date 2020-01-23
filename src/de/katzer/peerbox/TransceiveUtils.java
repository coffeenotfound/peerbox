package de.katzer.peerbox;

public class TransceiveUtils {
	
	public static char charFromBigEndian(byte[] buffer, int start) {
		return (char)((Byte.toUnsignedInt(buffer[start]) << 8) | Byte.toUnsignedInt(buffer[start+1]));
	}
	
	public static byte[] charToBigEndian(char value, byte[] buffer, int start) {
		buffer[start] = (byte)(((int)value >>> 8) & 0xFF);
		buffer[start+1] = (byte)((int)value & 0xFF);
		return buffer;
	}
	
//	public static String formatBinaryArray(byte[] array, int offset, int length) {
//		StringBuilder result = new StringBuilder("[");
//		for(int i = 0; i < length; i++) {
//			byte b = array[i+offset];
//			Integer.to
//		}
//		result.append(']');
//	}
}
