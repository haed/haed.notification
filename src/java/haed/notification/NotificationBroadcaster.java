package haed.notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.jersey.JerseyBroadcaster;

public class NotificationBroadcaster extends JerseyBroadcaster {
  
  /* members */
  
  private final Set<String> subscribedNotificationTypes = Collections.synchronizedSet(new HashSet<String>());
	
  
  public NotificationBroadcaster(final String id, final AtmosphereConfig config) {
    super(id, config);
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