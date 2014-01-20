package haed.notification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.atmosphere.cache.UUIDBroadcasterCache;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NotificationMgrImpl implements NotificationMgr {
	
	private static final Logger logger = Logger.getLogger(NotificationMgrImpl.class);
	
	
	private static NotificationMgrImpl notificationMgr = new NotificationMgrImpl();
	
	public static NotificationMgrImpl getInstance() {
		return notificationMgr;
	}
	
	private static final BroadcasterLifeCyclePolicy broadcasterLifeCyclePolicy = 
		new BroadcasterLifeCyclePolicy.Builder()
			.policy(BroadcasterLifeCyclePolicy.ATMOSPHERE_RESOURCE_POLICY.IDLE_DESTROY)
			.idleTime(2, TimeUnit.MINUTES)
			.build();
	
	
	static class NotificationCtnr {
	  
	  final long id;
	  final String type;
	  final Object message;
	  
	  NotificationCtnr(final long id, final String type, final Object message) {
	    this.id = id;
	    this.type = type;
	    this.message = message;
	  }
	}
	
	
	/* members */
	
	private GsonBuilder gsonBuilder = new GsonBuilder();
	
	private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
	
	private final BroadcasterCache broadcasterCache = new UUIDBroadcasterCache();
	
	
	private NotificationMgrImpl() {
	}
	
	protected NotificationBroadcaster getBroadcaster(final String channelID, final boolean createIfNull) {
		
	  NotificationBroadcaster broadCaster = BroadcasterFactory.getDefault().lookup(NotificationBroadcaster.class, channelID, false);
		if (broadCaster == null && createIfNull) {
			
			// instantiate new broadcaster
			broadCaster = BroadcasterFactory.getDefault().lookup(NotificationBroadcaster.class, channelID, true);
			
			broadCaster.getBroadcasterConfig().setBroadcasterCache(this.broadcasterCache);
			
			// set life policy explicitly
			broadCaster.setBroadcasterLifeCyclePolicy(broadcasterLifeCyclePolicy);
			
			// listen to destroy
			final NotificationBroadcaster _broadCaster = broadCaster;
			broadCaster.addBroadcasterLifeCyclePolicyListener(new BroadcasterLifeCyclePolicyListener() {
  			    
  			    @Override
  					public void onIdle() {}
  			    
  			    @Override
  					public void onEmpty() {}
  					
  			    @Override
  					public void onDestroy() {
  						
  						if (logger.isDebugEnabled())
  							logger.debug("destroy channel '" + channelID + "'");
  						
  						for (final String notificationType: _broadCaster.getSubscribedNotificationTypes()) {
  							try {
  								NotificationMgrImpl.this.unsubscribeInternal(channelID, notificationType);
  							} catch (final Exception e) {
  								logger.fatal("error on unsubscribe (on destroy), channelID: " + channelID + ", notificationType: " + notificationType);
  							}
  						}
  					}
  				});
		}
		
		return broadCaster;
	}
	
	@Override
	public void setGsonBuilder(final GsonBuilder gsonBuilder) {
	  this.gsonBuilder = gsonBuilder;
	}
	
	@Override
	public void subscribe(final String channelID, final String notificationType)
			throws Exception {
		
		final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
		if (broadcaster == null)
			throw new Exception("no broadcaster found, channelID: " + channelID);
		
		if (broadcaster.addSubscription(notificationType)) {
			
			if (logger.isDebugEnabled())
				logger.debug("channel '" + channelID + "' subscribes to notification type '" + notificationType + "'");
		
			// register channel for notification type
			Set<String> channelIDs = subscriptions.get(notificationType);
			if (channelIDs == null) {
				synchronized (subscriptions) {
				  channelIDs = subscriptions.get(notificationType);
					if (channelIDs == null) {
					  channelIDs = Collections.synchronizedSet(new HashSet<String>());
						subscriptions.put(notificationType, channelIDs);
					}
	      }
			}
			
			channelIDs.add(channelID);
		}
	}
	
	public void unsubscribe(final String channelID, final String notificationType)
			throws Exception {
		
		final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
		if (broadcaster == null)
			throw new Exception("no broadcaster found, channelID: " + channelID);
		
		if (broadcaster.removeSubscription(notificationType))
			unsubscribeInternal(channelID, notificationType);
	}
	
	protected void unsubscribeInternal(final String channelID, final String notificationType)
			throws Exception {
		
		// register channel for notification type
		Set<String> channelIDs = this.subscriptions.get(notificationType);
		if (channelIDs == null)
			return;
		
		synchronized (channelIDs) {
			
			// remove channel from subscriptions
		  channelIDs.remove(channelID);
			
			// check for last channel for given notification type
			if (channelIDs.isEmpty()) {
				
				// channelIDs is empty, maybe we can remove container
				synchronized (subscriptions) {
				  
				  channelIDs = subscriptions.get(notificationType);
					if (channelIDs != null && channelIDs.isEmpty())
						subscriptions.remove(notificationType);
				}
			}
		}
		
		if (logger.isDebugEnabled())
			logger.debug("" + channelIDs.size() + " subscriptions left for notification with type '" + notificationType + "'");
	}
	
	@Override
	public void sendNotification(final String notificationType, final Object source)
			throws Exception {
		
		final Set<String> channelIDs = this.subscriptions.get(notificationType);
		if (channelIDs == null || channelIDs.isEmpty())
			return;
		
		final Gson gson = this.gsonBuilder.create();
		
		// TODO: source should be object (not compatible with some clients outside)
    final String sourceJSON = gson.toJson(source);
    
		synchronized (channelIDs) {
			
			for (final String channelID: channelIDs) {
			  
				final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
				if (broadcaster == null) {
					
					// broadcaster does not exists anymore
					logger.warn("no broadcaster found, channelID: " + channelID + ", notificationType: " + notificationType);
					
					// ignore
					continue;
				}
				
				final long id = broadcaster.incrementAndGetID();
				final NotificationCtnr notificationCtnr = new NotificationCtnr(id, notificationType, sourceJSON);
		    
		    String message = gson.toJson(notificationCtnr);
		    message = message.length() + ":" + message;
			  
				if (logger.isDebugEnabled())
					logger.debug("send notification, channelID: " + channelID + ", id: " + id + ", notificationCtnr: " + notificationCtnr);
				
				// send directly
				broadcaster.broadcast(message);
			}
		}
	}
}