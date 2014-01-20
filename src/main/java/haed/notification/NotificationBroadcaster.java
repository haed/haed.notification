package haed.notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.jersey.JerseyBroadcaster;

public class NotificationBroadcaster extends JerseyBroadcaster {
  
  /* members */
  
  private final Set<String> subscribedNotificationTypes = Collections.synchronizedSet(new HashSet<String>());
	
  private final AtomicLong id = new AtomicLong();
  
  
  public NotificationBroadcaster(final String id, final AtmosphereConfig config) {
    super(id, config);
  }
  
  
  public long incrementAndGetID() {
    return this.id.incrementAndGet();
  }
	
	public boolean addSubscription(final String notificationType) {
		return this.subscribedNotificationTypes.add(notificationType);
	}
	
	public boolean removeSubscription(final String notificationType) {
		return this.subscribedNotificationTypes.remove(notificationType);
	}
	
	public Set<String> getSubscribedNotificationTypes() {
		return Collections.unmodifiableSet(subscribedNotificationTypes);
	}
}