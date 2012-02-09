package haed.notification;

import java.io.Serializable;

public class CachedMessage implements Serializable {

  private static final long serialVersionUID = 1L;
  
  public final Long serial;
  public final Object message;
//  public final long timestamp = System.currentTimeMillis();
  public CachedMessage next;

  public CachedMessage(final Long serial, final Object message, final CachedMessage next) {
    this.serial = serial;
    this.message = message;
    this.next = next;
  }
  
  @Override
  public String toString() {
    return "" + serial + ": " + message;
  }
}