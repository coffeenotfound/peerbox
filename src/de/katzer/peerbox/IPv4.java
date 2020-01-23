package de.katzer.peerbox;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class IPv4 {
	/**
	 * An array containing the four octets of this IPv4 address in
	 * big endian byte order.
	 */
	public byte[] octetsBE;
	
	public IPv4(byte[] octetsBE) {
		if(octetsBE == null || octetsBE.length < 4) {
			throw new IllegalArgumentException("Octet array must be non null and contain 4 elements");
		}
		
		this.octetsBE = octetsBE;
	}
	
	public boolean isValid() {
		return !(octetsBE[0] == 0 && octetsBE[1] == 0 && octetsBE[2] == 0 && octetsBE[3] == 0);
	}
	
	public boolean equals(IPv4 other) {
		return Arrays.equals(this.octetsBE, other.octetsBE);
	}
	
	public boolean equals(Object other) {
		return (other instanceof IPv4 ? equals((IPv4)other) : false);
	}
	
	public Inet4Address toJavaAddress() {
		try {
			return (Inet4Address)Inet4Address.getByAddress(octetsBE);
		}
		catch(UnknownHostException e) {
			throw new IllegalStateException("IPv4 is invalid", e);
		}
	}
	
	public static IPv4 fromBigEndian(byte[] array, int start) {
		byte[] octets = new byte[] {
			array[start],
			array[start+1],
			array[start+2],
			array[start+3],
		};
		return new IPv4(octets);
	}
	
	public static IPv4 fromLittleEndian(byte[] array, int start) {
		byte[] octets = new byte[] {
			array[start+3],
			array[start+2],
			array[start+1],
			array[start],
		};
		return new IPv4(octets);
	}
	
	@Override
	public String toString() {
		return (octetsBE[0] & 0xFF) + "." + (octetsBE[1] & 0xFF) + "." + (octetsBE[2] & 0xFF) + "." + (octetsBE[3] & 0xFF);
	}
	
	public static IPv4 fromJavaAddress(InetAddress address) {
		if(address instanceof Inet4Address) {
			return new IPv4(address.getAddress());
		}
		else {
			throw new IllegalStateException("Address not instanceof Inet4Address");
		}
	}
}
