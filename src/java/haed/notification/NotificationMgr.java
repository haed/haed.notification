package haed.notification;

import haed.notification.gson.JSONStreamingOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.google.gson.Gson;

/**
 * TODO @haed [haed]: refactor to interface and impl-class
 */
public class NotificationMgr {
	
	private static final Logger logger = Logger.getLogger(NotificationMgr.class);
	
	
	private static NotificationMgr notificationMgr = new NotificationMgr();
	
	public static NotificationMgr getInstance() {
		return notificationMgr;
	}
	
	public static void useExecutorService(ScheduledExecutorService service) {
	  NotificationBroadcaster.useExecutorService(service);
	}
	
	
	
	private static final BroadcasterLifeCyclePolicy broadcasterLifeCyclePolicy = 
		new BroadcasterLifeCyclePolicy.Builder()
			.policy(BroadcasterLifeCyclePolicy.ATMOSPHERE_RESOURCE_POLICY.NEVER)
			.build();
	
	
	/* members */
	
	private final Map<String, Map<String, NotificationFilter>> subscriptions = new HashMap<String, Map<String, NotificationFilter>>();
	
	private final AtomicLong notificationID = new AtomicLong(1);
	
	
	private NotificationMgr() {
	}
	
	private long createNotificationID() {
	  return notificationID.incrementAndGet();
	}
	
	protected NotificationBroadcaster getBroadcaster(final String channelID, final boolean createIfNull) {
		
		NotificationBroadcaster broadCaster = (NotificationBroadcaster) BroadcasterFactory.getDefault().lookup(NotificationBroadcaster.class, channelID, false);
		if (broadCaster == null && createIfNull) {
			
			// instantiate new broadcaster
			broadCaster = (NotificationBroadcaster) BroadcasterFactory.getDefault().lookup(NotificationBroadcaster.class, channelID, true);
			
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
								NotificationMgr.this.unsubscribeInternal(channelID, notificationType);
							} catch (final Exception e) {
								logger.fatal("error on unsubscribe (on destroy), channelID: " + channelID + ", notificationType: " + notificationType);
							}
						}
					}
				});
		}
		
		return broadCaster;
	}
	
	public void subscribe(final String channelID, final String notificationType)
			throws Exception {
		subscribe(channelID, notificationType, null);
	}
	
	public void subscribe(final String channelID, final String notificationType, final NotificationFilter notificationFilter)
			throws Exception {
		
		final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
		if (broadcaster == null)
			throw new Exception("no broadcaster found, channelID: " + channelID);
		
		if (broadcaster.addSubscription(notificationType)) {
			
			if (logger.isDebugEnabled())
				logger.debug("channel '" + channelID + "' subscribes to notification type '" + notificationType + "'");
		
			// register channel for notification type
			Map<String, NotificationFilter> channels = subscriptions.get(notificationType);
			if (channels == null) {
				synchronized (subscriptions) {
					channels = subscriptions.get(notificationType);
					if (channels == null) {
						channels = new HashMap<String, NotificationFilter>();
						subscriptions.put(notificationType, channels);
					}
	      }
			}
			
			synchronized (channels) {
				channels.put(channelID, notificationFilter);
			}
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
		Map<String, NotificationFilter> channels = subscriptions.get(notificationType);
		if (channels == null)
			return;
		
		synchronized (channels) {
			
			// remove channel from subscriptions
			channels.remove(channelID);
			
			// check for last channel for given notification type
			if (channels.isEmpty()) {
				
				// channelIDs is empty, maybe we can remove container
				synchronized (subscriptions) {
					channels = subscriptions.get(notificationType);
					if (channels != null && channels.isEmpty())
						subscriptions.remove(notificationType);
				}
			}
		}
		
		if (logger.isDebugEnabled())
			logger.debug("" + channels.size() + " subscriptions left for notification with type '" + notificationType + "'");
	}
	
	
	private JSONObject buildNotificationCtnr(final Long id, final String notificationType, final Object message)
			throws JSONException {
		
		final Gson gson = JSONStreamingOutput.createGson();
		
		final JSONObject notificationCtnr = new JSONObject();
		notificationCtnr.put("id", String.valueOf(id));
		notificationCtnr.put("type", notificationType);
		notificationCtnr.put("message", gson.toJson(message));
		
		return notificationCtnr;
	}
	
	public void sendNotification(final String notificationType, final Object source)
			throws Exception {
		
		final Map<String, NotificationFilter> channelIDs = subscriptions.get(notificationType);
		if (channelIDs == null || channelIDs.isEmpty())
			return;
		
		// build up notification
		final long currentNotificationID = createNotificationID();
		final JSONObject notificationCtnr = buildNotificationCtnr(currentNotificationID, notificationType, source);
		
		
		String message = JSONStreamingOutput.createGson().toJson(notificationCtnr);
  	message = ((String) message).length() + ":" + message;
		
		synchronized (channelIDs) {
			
			for (final Map.Entry<String, NotificationFilter> entry: channelIDs.entrySet()) {
				
				final String channelID = entry.getKey();
				
				final NotificationBroadcaster broadcaster = getBroadcaster(channelID, false);
				if (broadcaster == null) {
					
					// broadcaster does not exists anymore
					logger.warn("no broadcaster found, channelID: " + channelID + ", notificationType: " + notificationType + ", notificationCtnr: " + notificationCtnr);
					
					// ignore
					continue;
				}
				
				// check for filter
				final NotificationFilter filter = entry.getValue();
				if (filter != null && filter.isValid(source) == false)
					continue;
					
				if (logger.isDebugEnabled())
					logger.debug("send notification, channelID: " + channelID + ", notificationID: " + currentNotificationID + ", notificationCtnr: " + notificationCtnr);
				
				// send directly
				sendData(broadcaster, message);
			}
		}
	}
	
	private void sendData(final NotificationBroadcaster broadcaster, final Object message) {
		broadcaster.send(message);
	}
}