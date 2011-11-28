package haed.notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereServlet.AtmosphereConfig;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;
import org.atmosphere.jersey.JerseyBroadcaster;

public class NotificationBroadcaster extends JerseyBroadcaster {
	
	private static final Logger logger = Logger.getLogger(NotificationBroadcaster.class);
	
	
	private static final Map<String, Long> destructionQueue = Collections.synchronizedMap(new LinkedHashMap<String, Long>());
	
	static {
		
		new ScheduledThreadPoolExecutor(1).scheduleWithFixedDelay(new Runnable() {
			
				public void run() {
					
					if (logger.isDebugEnabled())
						logger.debug("check destruction queue, " + destructionQueue.size() + " elements before check");
					
					final long t = System.currentTimeMillis() - (1000 * 60 * 5); // destroy all broadcaster which are more than 5 minutes in destruction queue
					
					synchronized (destructionQueue) {
						for (final Iterator<Map.Entry<String, Long>> iter = destructionQueue.entrySet().iterator(); iter.hasNext(); ) {
							final Map.Entry<String, Long> entry = iter.next();
							if (entry.getValue().longValue() < t) {
								
								// destroy
								final NotificationBroadcaster broadcaster = NotificationMgr.getInstance().getBroadcaster(entry.getKey(), false);
								if (broadcaster == null)
									logger.warn("no broadcaster found, channelID: " + entry.getKey());
								
								broadcaster.destroy();
								iter.remove();
							} else {
								
								// entry is newer, break
								return;
							}
						}
					}
				}
			}, 0, 30, TimeUnit.SECONDS);
	}
	
	
	private final Set<String> subscribedNotificationTypes = Collections.synchronizedSet(new HashSet<String>());
	
	private boolean empty = false;
	private boolean resumed = false;
	
	public NotificationBroadcaster(final String id, final AtmosphereConfig config) {
		super(id, config);
  }
	
	@Override
	public AtmosphereResource<?, ?> addAtmosphereResource(AtmosphereResource<?, ?> resource) {
		
		resource = super.addAtmosphereResource(resource);
		
		if (this.empty && getAtmosphereResources().isEmpty() == false) {
			
			if (logger.isDebugEnabled())
				logger.debug("remove channel '" + getID() + "' from destruction queue");
			
			this.empty = false;
			destructionQueue.remove(getID());
		}
		
		return resource;
	}
	
	@Override
	public AtmosphereResource<?, ?> removeAtmosphereResource(AtmosphereResource resource) {
		
		resource = super.removeAtmosphereResource(resource);
		
		if (this.empty == false && getAtmosphereResources().isEmpty()) {
			
			if (logger.isDebugEnabled())
				logger.debug("add channel '" + getID() + "' to destruction queue");
			
			this.empty = true;
			destructionQueue.put(getID(), System.currentTimeMillis());
		}
		
		return resource;
	}
	
	public void send(final Object message) {
		
		if (resumed || resources.isEmpty())
			broadcasterCache.addToCache(null, message);
		else {
			try {
				synchronized (this) {
					this.broadcast(message).get(30, TimeUnit.SECONDS);
				}
			} catch (final Exception e) {
				logger.fatal("error on sending message, messages will be lost: " + message, e);
			}
		}
	}
	
	
	@Override
	public void destroy() {
		
		super.destroy();
		
		// TODO @haed [haed]: atmosphere does not call BroadcasterLifeCyclePolicyListener#onDestroy on explicitly calling broadcaster#destroy().
		//  => we have to override to trigger custom logic
		//  - remove if atmosphere works like expected
		//  GitHub issue: https://github.com/Atmosphere/atmosphere/issues/83
		for (final BroadcasterLifeCyclePolicyListener b: lifeCycleListeners)
      b.onDestroy();
	}
	
	public boolean addSubscription(final String notificationType) {
		return subscribedNotificationTypes.add(notificationType);
	}
	
	public boolean removeSubscription(final String notificationType) {
		return subscribedNotificationTypes.remove(notificationType);
	}
	
	public Set<String> getSubscribedNotificationTypes() {
		return Collections.unmodifiableSet(subscribedNotificationTypes);
	}
	
	public void setResumed(final boolean resumed) {
		this.resumed = resumed;
	}
	
	public boolean isAvailable() {
		return resumed == false;
	}
}