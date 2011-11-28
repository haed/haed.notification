package haed.notification;

import haed.notification.gson.JSONStreamingOutput;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy;
import org.atmosphere.cpr.BroadcasterLifeCyclePolicyListener;

import com.google.gson.Gson;

/**
 * TODO @haed [haed]: refactor to interface and impl-class
 */
public class NotificationMgr {
	
	private static final Logger logger = Logger.getLogger(NotificationMgr.class);
	
	
//	private static final String KEY_SUBSCRIPTIONS = "haed.soa.notification.subscriptions";
	
	private static NotificationMgr notificationMgr = new NotificationMgr();
	
	public static NotificationMgr getInstance() {
		return notificationMgr;
	}
	
	
	
	private static final BroadcasterLifeCyclePolicy broadcasterLifeCyclePolicy = 
		new BroadcasterLifeCyclePolicy.Builder()
			.policy(BroadcasterLifeCyclePolicy.ATMOSPHERE_RESOURCE_POLICY.NEVER)
			.build();
	
	
	/* members */
	
	private final Map<String, Map<String, NotificationFilter>> subscriptions = new HashMap<String, Map<String, NotificationFilter>>();
	
	
	
	/**
	 * Holds all queued notifications by channel: mapping: channelID -> notificationID[].
	 */
	private final Map<String, LinkedList<Long>> queues = new HashMap<String, LinkedList<Long>>();
	
	private final AtomicInteger notificationID = new AtomicInteger(1);
	
	private final long time2NotificationIdThreshold = 1000;
	private long currentTime2NotificationId = 0;
	private final TreeMap<Long, Long> time2NotificationId = new TreeMap<Long, Long>();
	
	private final TreeMap<Long, String> notificationCache = new TreeMap<Long, String>();
	
	
	
	private NotificationMgr() {
	}
	
	private long createNotificationID() {
		
		final long id = notificationID.incrementAndGet();
		
		final long now = System.currentTimeMillis();
		if (now - currentTime2NotificationId > time2NotificationIdThreshold) {
			currentTime2NotificationId = now;
			time2NotificationId.put(currentTime2NotificationId, id);
		}
		
		return id;
	}
	
	
	/* notification cache */
	
	public void checkNotificationCacheTimeout(long timeoutInMs) {
		
		final long now = System.currentTimeMillis();
		while (!time2NotificationId.isEmpty() && (time2NotificationId.firstKey() < now - timeoutInMs)) {
			final long notificationId = time2NotificationId.pollFirstEntry().getValue();
			synchronized (notificationCache) {
				while (!notificationCache.isEmpty() && notificationCache.firstKey() < notificationId)
					notificationCache.pollFirstEntry();
			}
		}
		
		if (logger.isDebugEnabled())
			logger.debug("cleaned up notification cache, " + notificationCache.size() + " notifications left in cache");
	}
	
	private void queueNotification(final String channelID, final long notificationID, final String notificationCtnr) {
		
		if (logger.isDebugEnabled())
			logger.debug("queue notification, channelID: " + channelID + ", notificationID: " + notificationID + ", notificationCtnr: " + notificationCtnr);
		
		// cache notification
		synchronized (notificationCache) {
			notificationCache.put(notificationID, notificationCtnr);
		}
		
		// add cached notification to channel queue
		LinkedList<Long> queue = queues.get(channelID);
		if (queue == null) {
			synchronized (queues) {
				queue = queues.get(channelID);
				if (queue == null) {
					queue = new LinkedList<Long>();
					queues.put(channelID, queue);
				}
			}
		}
		
		synchronized (queue) {
			queue.add(notificationID);
		}
	}
	
	public void processQueue(final String channelID, final NotificationBroadcaster broadcaster) {
		
		final LinkedList<Long> queue = queues.get(channelID);
		if (queue == null || queue.isEmpty()) {
			// no queue for this channel found
			return;
		}
		
		if (logger.isDebugEnabled())
			logger.debug("send " + queue.size() + " queued notifications to channel '" + channelID + "'");
		
		synchronized (queue) {
			
			// build up data
			final StringBuilder data = new StringBuilder();
			for (final Long notificationID: queue)
				// TODO [haed]: maybe notification is not cached anymore (e.g. due timeout), we need a failure scenario (e.g. a special notification)
				data.append(notificationCache.get(notificationID));
			
			// send
			if (data != null && data.length() > 0) {
				
				try {
					
					sendData(broadcaster, data.toString());
					
					// send was successful, clear queue
					queue.clear();
					
				} catch (final Exception e) {
					logger.fatal("error on sending queue data", e);
				}
			}
		}
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
				
					public void onIdle() {}
					public void onEmpty() {}
					
					public void onDestroy() {
						
						if (logger.isDebugEnabled())
							logger.debug("destroy channel '" + channelID + "'");
						
						synchronized (queues) {
							queues.remove(channelID);
						}
						
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
		
		
		
//		// register notification at channel session
//		HttpSession channelSession = HttpSessionMgr.getInstance().getSession(channelID, true);
//		
//		Set<String> notificationTypes;
//		synchronized (channelSession) {
//			notificationTypes = (Set<String>) channelSession.getAttribute(KEY_SUBSCRIPTIONS);
//			if (notificationTypes == null) {
//				notificationTypes = new HashSet<String>();
//				channelSession.setAttribute(KEY_SUBSCRIPTIONS, notificationTypes);
//			}
//		}
//		
//		synchronized (notificationTypes) {
//			notificationTypes.add(notificationType);
//		}
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
	
	
//	public String buildNotificationCtnr(final String notificationType, final Object source) {
//		return buildNotificationCtnr(null, notificationType, source);
//	}
	
	private String buildNotificationCtnr(final Long id, final String notificationType, final Object source) {
		
		final Gson gson = JSONStreamingOutput.createGson();
		
		final Map<String, String> notification = new HashMap<String, String>();
		if (id == null)
			notification.put("id", String.valueOf(createNotificationID()));
		else
			notification.put("id", String.valueOf(id));
		
		notification.put("type", notificationType);
		notification.put("message", gson.toJson(source));
		
		final String jsonNotification = gson.toJson(notification);
		return new StringBuilder()
			.append(jsonNotification.length()).append(":").append(jsonNotification).toString();
	}
	
	public void sendNotification(final String notificationType, final Object source) {
		
		final Map<String, NotificationFilter> channelIDs = subscriptions.get(notificationType);
		if (channelIDs == null || channelIDs.isEmpty())
			return;
		
		// build up notification
		final long currentNotificationID = createNotificationID();
		final String notificationCtnr = buildNotificationCtnr(currentNotificationID, notificationType, source);
		
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
				
				if (broadcaster.isAvailable()) {
					
					if (logger.isDebugEnabled())
						logger.debug("send notification, channelID: " + channelID + ", notificationID: " + currentNotificationID + ", notificationCtnr: " + notificationCtnr);
					
					// send directly
					sendData(broadcaster, notificationCtnr);
					
				} else {
					
					// broadcaster is absent, queue notification
					queueNotification(channelID, currentNotificationID, notificationCtnr);
				}
			}
		}
	}
	
	private void sendData(final NotificationBroadcaster broadcaster, final String data) {
		
		// TODO [haed]: (atmosphere 0.7.2, still in 0.8) Atmosphere bugfix for long-polling, jersey, jetty-scenario
		//   - if more than 1 message is triggered to broadcast within one async-write cycle the internal state gets broken (JerseyBroadcasterUtil#40)
		//   - all messages would be send in parallel, but on long-polling the response will be resumed (calling listeners is not synchronized)
		// => GitHub issue: https://github.com/Atmosphere/atmosphere/issues/81
		try {
			synchronized (broadcaster) {
				broadcaster.broadcast(data).get(30, TimeUnit.SECONDS);
			}
		} catch (final Exception e) {
			logger.fatal("error on sending message, messages will be lost: " + data, e);
		}
		
		// this should be the 'normal' way, on illegal state the message go to the attached cache (if exists)
		// -> but we cache outside of atmosphere (with some optimization), so we need to synchronized/blocking-approach
//		broadcaster.broadcast(data);
	}
}