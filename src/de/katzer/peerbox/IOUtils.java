package de.katzer.peerbox;

import java.io.Closeable;

public final class IOUtils {
	
	public static void closeSilently(Closeable closable) {
		try {
			if(closable != null) closable.close();
		}
		catch(Exception e) {}
	}
}
