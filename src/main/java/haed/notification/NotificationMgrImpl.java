package haed.notification;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.Broadcaster;
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
	
	private ChannelAdapter channelAdapter = new UUIDChannelAdapter();
	
	private final Map<String, Set<String>> topicsByChannel = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> channelsByTopic = new ConcurrentHashMap<>();
	private final Map<String, AtomicLong> notificationIDsByChannel = new ConcurrentHashMap<>();
	
	private NotificationMgrImpl() {
	}
	
	
	private long incrementAndGetNotificationID(final String channelID) {
    return this.notificationIDsByChannel.get(channelID).incrementAndGet();
  }
	
	protected Broadcaster getBroadcaster(final String channelID, final boolean createIfNull) {
		
	  Broadcaster broadCaster = BroadcasterFactory.getDefault().lookup(channelID, false);
		if (broadCaster == null && createIfNull) {
			
			// instantiate new broadcaster
			broadCaster = BroadcasterFactory.getDefault().lookup(channelID, true);
			
			this.notificationIDsByChannel.put(channelID, new AtomicLong(0));
			this.topicsByChannel.put(channelID, Collections.synchronizedSet(new HashSet<String>()));
			
//			broadCaster.getBroadcasterConfig().setBroadcasterCache(this.broadcasterCache);
			
			// set life policy explicitly
			broadCaster.setBroadcasterLifeCyclePolicy(broadcasterLifeCyclePolicy);
			
			// listen to destroy
//			final NotificationBroadcaster _broadCaster = broadCaster;
			broadCaster.addBroadcasterLifeCyclePolicyListener(new BroadcasterLifeCyclePolicyListener() {
  			    
  			    @Override
  					public void onIdle() {}
  			    
  			    @Override
  					public void onEmpty() {}
  					
  			    @Override
  					public void onDestroy() {
  						
  						if (logger.isDebugEnabled())
  							logger.debug("destroy channel '" + channelID + "'");
  						
  						final Collection<String> c = topicsByChannel.remove(channelID);
  						if (c != null) {
  						  
  						  for (final String topic: c)
  						    unsubscribeInternal(channelID, topic);
  						}
  						
//  						for (final String notificationType: _broadCaster.getSubscribedNotificationTypes()) {
//  							try {
//  								NotificationMgrImpl.this.unsubscribeInternal(channelID, notificationType);
//  							} catch (final Exception e) {
//  								logger.fatal("error on unsubscribe (on destroy), channelID: " + channelID + ", notificationType: " + notificationType);
//  							}
//  						}
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
	public void subscribe(final String channelID, final String topic)
			throws Exception {
		
	  final Set<String> set = this.topicsByChannel.get(channelID);
	  if (set == null)
	    throw new Exception("no broadcaster found for channelID '" + channelID +  "'");
		
		if (set.add(topic)) {
			
			if (logger.isDebugEnabled())
				logger.debug("channel '" + channelID + "' subscribes to topic '" + topic + "'");
			
			Set<String> channelIDs = this.channelsByTopic.get(topic);
			if (channelIDs == null) {
			  
				synchronized (this.channelsByTopic) {
				  
				  channelIDs = this.channelsByTopic.get(topic);
					if (channelIDs == null) {
					  
					  channelIDs = Collections.synchronizedSet(new HashSet<String>());
						this.channelsByTopic.put(topic, channelIDs);
					}
	      }
			}
			
			channelIDs.add(channelID);
		}
	}
	
	public void unsubscribe(final String channelID, final String topic)
			throws Exception {
		
//		final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
//		if (broadcaster == null)
//			throw new Exception("no broadcaster found, channelID: " + channelID);
		
		if (this.topicsByChannel.get(channelID).remove(topic))
			unsubscribeInternal(channelID, topic);
	}
	
	private void unsubscribeInternal(final String channelID, final String topic) {
		
		// register channel for notification type
		Set<String> channelIDs = this.channelsByTopic.get(topic);
		if (channelIDs == null)
			return;
		
		synchronized (channelIDs) {
			
			// remove channel from subscriptions
		  channelIDs.remove(channelID);
			
			// check for last channel for given notification type
			if (channelIDs.isEmpty()) {
				
				// channelIDs is empty, maybe we can remove container
				synchronized (channelsByTopic) {
				  
				  channelIDs = channelsByTopic.get(topic);
				  if (channelIDs != null && channelIDs.isEmpty())
						channelsByTopic.remove(topic);
				}
			}
		}
		
		if (logger.isDebugEnabled())
			logger.debug("" + channelIDs.size() + " subscriptions left for notification with type '" + topic + "'");
	}
	
	
	@Override
	public void setChannelAdapter(final ChannelAdapter channelAdapter) {
    this.channelAdapter = channelAdapter;
  }
	
	@Override
	public ChannelAdapter getChannelAdapter() {
	  return this.channelAdapter;
	}
	
	@Override
	public void sendNotification(final String topic, final Object payload)
			throws Exception {
		
		final Set<String> channelIDs = this.channelsByTopic.get(topic);
		if (channelIDs == null || channelIDs.isEmpty())
			return;
		
		final Gson gson = this.gsonBuilder.create();
		
		synchronized (channelIDs) {
			
			for (final String channelID: channelIDs) {
			  
				final Broadcaster broadcaster = getBroadcaster(channelID, false);
				if (broadcaster == null) {
					
					// broadcaster does not exists anymore
					logger.warn("no broadcaster found, channelID: " + channelID + ", topic: " + topic);
					
					// ignore
					continue;
				}
				
				final long id = incrementAndGetNotificationID(channelID);
				final NotificationCtnr notificationCtnr = new NotificationCtnr(id, topic, payload);
		    
		    String message = gson.toJson(notificationCtnr);
		    message = message.length() + ":" + message;
			  
				if (logger.isDebugEnabled())
					logger.debug("send notification, channelID: " + channelID + ", id: " + id + ", ctnr: " + notificationCtnr);
				
				// send directly
				broadcaster.broadcast(message);
			}
		}
	}
}