package haed.notification;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class NotificationApplication extends Application {
	
	private final Set<Object> singletons = new HashSet<>();
	
	public NotificationApplication() {
		this.singletons.add(new NotificationAPI());
	}
	
	@Override
	public Set<Object> getSingletons() {
	  return this.singletons;
  }
}