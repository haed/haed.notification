package haed.notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereServlet.AtmosphereConfig;
import org.atmosphere.jersey.JerseyBroadcaster;

public class NotificationBroadcaster extends JerseyBroadcaster {
	
	private static final Logger logger = Logger.getLogger(NotificationBroadcaster.class);
	
	
	private static final Map<String, Long> destructionQueue = Collections.synchronizedMap(new LinkedHashMap<String, Long>());


  private static final long DESTRUCTION_CHECK_CYCLE_IN_SEC = 30;

	static AtomicBoolean alreadySetExecutor=new AtomicBoolean(false);
	static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
	static Thread destructionCheck=new Thread() {
      
      @Override
      public void run() {
        
        if (logger.isDebugEnabled())
          logger.debug("check destruction queue, " + destructionQueue.size() + " elements before check");
        
//        // also cleanup caches for all serial broad caster caches
//        SerialBroadcasterCache.cleanUp(1000 * 60 * 5); // 5 minutes
        
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
    };
	
	static {
    scheduledThreadPoolExecutor.scheduleWithFixedDelay(destructionCheck, 0, DESTRUCTION_CHECK_CYCLE_IN_SEC, TimeUnit.SECONDS);
	}
	
	
	private final Set<String> subscribedNotificationTypes = Collections.synchronizedSet(new HashSet<String>());
	
	private boolean empty = false;
	
	
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
	
	
	Long getActualSerial() {
	  if (broadcasterCache instanceof SerialBroadcasterCache)
	    return ((SerialBroadcasterCache) broadcasterCache).getActualSerial();
	  else
	    return -1L;
	}
	
	
	void cleanUpCache(Long serial) {
	  ((SerialBroadcasterCache) broadcasterCache).cleanUp(serial);
	}
	
	public void send(final Object message) {
	  
	  if (broadcasterCache instanceof SerialBroadcasterCache)
  	  // always cache if its a serial broad caster cache instance
	    ((SerialBroadcasterCache) broadcasterCache)._addToCache(message);
	  
	  broadcast(message);
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

  public static void useExecutorService(ScheduledExecutorService service) {
    if (alreadySetExecutor.getAndSet(true) == false) {
      scheduledThreadPoolExecutor.shutdownNow();
      service.scheduleWithFixedDelay(destructionCheck, 0, DESTRUCTION_CHECK_CYCLE_IN_SEC, TimeUnit.SECONDS);
    }
  }
}