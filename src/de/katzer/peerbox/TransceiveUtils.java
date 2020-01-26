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
	
	public static long longFromBigEndian(byte[] buffer, int start) {
		return Byte.toUnsignedLong(buffer[start+7])
			| (Byte.toUnsignedLong(buffer[start+6]) << 8)
			| (Byte.toUnsignedLong(buffer[start+5]) << 16)
			| (Byte.toUnsignedLong(buffer[start+4]) << 24)
			| (Byte.toUnsignedLong(buffer[start+3]) << 32)
			| (Byte.toUnsignedLong(buffer[start+2]) << 40)
			| (Byte.toUnsignedLong(buffer[start+1]) << 48)
			| (Byte.toUnsignedLong(buffer[start+0]) << 56);
	}
	
	public static byte[] longToBigEndian(long value, byte[] buffer, int start) {
		buffer[start+7] = (byte)(value & 0xFF);
		buffer[start+6] = (byte)((value >>> 8) & 0xFF);
		buffer[start+5] = (byte)((value >>> 16) & 0xFF);
		buffer[start+4] = (byte)((value >>> 24) & 0xFF);
		buffer[start+3] = (byte)((value >>> 32) & 0xFF);
		buffer[start+2] = (byte)((value >>> 40) & 0xFF);
		buffer[start+1] = (byte)((value >>> 48) & 0xFF);
		buffer[start+0] = (byte)((value >>> 56) & 0xFF);
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
