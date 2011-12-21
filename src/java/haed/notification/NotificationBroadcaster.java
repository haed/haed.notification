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

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereServlet.AtmosphereConfig;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.jersey.JerseyBroadcaster;
import org.atmosphere.jersey.util.JerseyBroadcasterUtil;

import com.sun.jersey.spi.container.ContainerResponse;

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
	
	
	// overrides the underlying broadcaster cache
	private final NotificationBroadcasterCache broadcasterCache = new NotificationBroadcasterCache();
	
	
//	private boolean send = false;
	private boolean empty = false;
//	private boolean resumed = false;
	
	public NotificationBroadcaster(final String id, final AtmosphereConfig config) {
		super(id, config);
  }
	
	@Override
	public AtmosphereResource<?, ?> addAtmosphereResource(AtmosphereResource<?, ?> resource) {
		
		resource = super.addAtmosphereResource(resource);
		
		if (this.empty && getAtmosphereResources().isEmpty() == false) {
			
			if (logger.isDebugEnabled())
				logger.debug("remove channel '" + getID() + "' from destruction queue");
			
//			this.send = false;
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
	
	@Override
	protected void checkCachedAndPush(final AtmosphereResource<?, ?> r, final AtmosphereResourceEvent e) {
		
		// make nothing
		
//    retrieveTrackedBroadcast(r, e);
//    if (e.getMessage() instanceof List && !((List) e.getMessage()).isEmpty()) {
//        HttpServletRequest.class.cast(r.getRequest()).setAttribute(CACHED, "true");
//        // Must make sure execute only one thread
//        synchronized (r) {
//            broadcast(r, e);
//        }
//    }
	}
	
	
	// TODO @haed [haed]: remove if long-polling bug is solved (issue https://github.com/Atmosphere/atmosphere/issues/81)
	@Override
  protected void broadcast(final AtmosphereResource<?, ?> r, final AtmosphereResourceEvent e) {
		
		final ContainerResponse containerResponse = (ContainerResponse) ((HttpServletRequest) r.getRequest()).getAttribute(FrameworkConfig.CONTAINER_RESPONSE);
    if (containerResponse == null || resources.isEmpty()) {
    	
    	if (logger.isInfoEnabled())
    		logger.info("resource is not connected, re-add message to cache, channelID: " + getID() + ", message: " + e.getMessage());
    	
    	// resource is not connected, re-queue
    	broadcasterCache.addToCache(null, e.getMessage());
    	
    } else
    	// resource is connected, go forward
    	JerseyBroadcasterUtil.broadcast(r, e);
  }
	
	private boolean send = false;
	
	public void send(final Object message) {
		
		// workaround for atmosphere issue, avoid flushing 
		// (never send more than 1 message, on 1 message the connection will be closed, otherwise only flushed => triggers reconnect)
		// => GitHub issue: https://github.com/Atmosphere/atmosphere/issues/87
		
		if (send || resources.isEmpty())
			broadcasterCache.addToCache(null, message);
		else {
		
			synchronized (this) {
				
				// re-check synchronized
				if (send || resources.isEmpty())
					broadcasterCache.addToCache(null, message);
				
				try {
					
					this.send = true;
					super.broadcast(message).get(30, TimeUnit.SECONDS);
					
				} catch (final Exception e) {
					logger.fatal("error on sending message, maybe message will be lost: " + message, e);
				} finally {
					send = false;
				}
			}
		}
	}
	
	protected void processCache() {
		for (final Object message: broadcasterCache.retrieveFromCache(null))
			send(message);
	}
	
	
//	@Override
//	protected boolean retrieveTrackedBroadcast(final AtmosphereResource r, final AtmosphereResourceEvent e) {
//	    List<?> missedMsg = notificationBroadcasterCache.retrieveFromCache(r);
//	    if (!missedMsg.isEmpty()) {
//	        e.setMessage(missedMsg);
//	        return true;
//	    }
//	    return false;
//	}
	
	
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

  public static void useExecutorService(ScheduledExecutorService service) {
    if(alreadySetExecutor.getAndSet(true)==false) {
      scheduledThreadPoolExecutor.shutdownNow();
      service.scheduleWithFixedDelay(destructionCheck, 0, DESTRUCTION_CHECK_CYCLE_IN_SEC, TimeUnit.SECONDS);
    }
    // TODO Auto-generated method stub
    
  }
	
//	public void setResumed(final boolean resumed) {
//		this.resumed = resumed;
//	}
//	
//	public boolean isAvailable() {
//		return resumed == false;
//	}
}