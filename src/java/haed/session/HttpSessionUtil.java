package haed.session;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.UUID;

public final class HttpSessionUtil {
	
	private static Random random;

  static {
  	
  	// initialize random
    try {
      random = (Random) Class.forName("java.security.SecureRandom").newInstance();
    } catch (final Exception e) {
      random = new Random();
    }
    
    String host = null;
    try {
    	host = InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException e) {
    	host = "localhost";
    }
    
    // initialize seed
    long seed = System.currentTimeMillis();
    
    final char[] entropy = ("" + random.hashCode() + "." + seed + ".haed@" + host + "." + UUID.randomUUID().toString()).toCharArray();
    for (int i = 0; i < entropy.length; i++)
      seed ^= ((byte) entropy[i]) << ((i % 8) * 8);
    
    random.setSeed(seed);
  }
	
	public static String generateSessionId() {
		
		final byte[] bytes = new byte[16];
    synchronized (random) {
    	random.nextBytes(bytes);
    }

    // render the result as a string of hexadecimal digits
    final StringBuilder id = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      byte b1 = (byte) ((bytes[i] & 0xf0) >> 4);
      byte b2 = (byte) (bytes[i] & 0x0f);

      if (b1 < 10)
        id.append((char) ('0' + b1));
      else
        id.append((char) ('A' + (b1 - 10)));
      if (b2 < 10)
        id.append((char) ('0' + b2));
      else
        id.append((char) ('A' + (b2 - 10)));
    }
    
    return id.toString();
  }
}