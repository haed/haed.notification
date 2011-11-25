package haed.notification.atmosphere;

import org.atmosphere.cpr.AtmosphereServlet.AtmosphereConfig;
import org.atmosphere.jersey.JerseyBroadcaster;

public class NotificationBroadcaster extends JerseyBroadcaster {
	
	private boolean resumed = false;
	
	public NotificationBroadcaster(final String id, final AtmosphereConfig config) {
	  
		super(id, config);
  }
	
	public void setResumed(final boolean resumed) {
		this.resumed = resumed;
	}
	
	public boolean isAvailable() {
		return resumed == false;
	}
}