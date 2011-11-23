package haed.notification;

import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.jersey.JerseyBroadcaster;

/**
 * Atmosphere HOTFIX: On long polling broadcasting data within short time period some data will get lost (jersey environment):
 *   - jersey, long-polling, jetty
 *   - atmosphere resources get resumed after sending first data
 *   - all data send in resumed state get lost, because ContainerResponse is null in assigned jersey request
 *     (org.atmosphere.jersey.util.JerseyBroadcasterUtil#38)
 *   => workaround: track connection state via event listener (see NotificationAPI)
 */
public class PatchedBroadcaster extends JerseyBroadcaster {
	
	private boolean connected = true;
	
	public PatchedBroadcaster(final String id, final AtmosphereServlet.AtmosphereConfig config) {
		super(id, config);
	}
	
	public boolean isConnected() {
//		return true;
		return this.connected;
	}
	
	public void setConnected(final boolean connected) {
		this.connected = connected;
	}
}