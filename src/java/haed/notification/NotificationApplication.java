package haed.notification;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class NotificationApplication extends Application {
	
	private final Set<Object> singletons = new HashSet<Object>();

	public NotificationApplication() {
		singletons.add(new NotificationAPI());
	}
	
	public Set<Object> getSingletons() {
	  return singletons;
  }
}