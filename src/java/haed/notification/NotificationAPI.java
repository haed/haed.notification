package haed.notification;

import haed.notification.atmosphere.NotificationBroadcaster;
import haed.session.HttpSessionMgr;

import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListener;
import org.atmosphere.jersey.SuspendResponse;
import org.atmosphere.jersey.SuspendResponse.SuspendResponseBuilder;

@Path("/")
public class NotificationAPI {
	
	private static final Logger logger = Logger.getLogger(NotificationAPI.class);
	
	private static final NotificationMgr notificationMgr = NotificationMgr.getInstance();
	
	public static final CacheControl cacheControl_cacheNever = new CacheControl();
	
	static {
		cacheControl_cacheNever.setMaxAge(0);
		cacheControl_cacheNever.setNoCache(true);
	}
	
	@GET
	@Path("/createChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response createChannel() {
		return Response.ok(notificationMgr.createChannelID()).cacheControl(cacheControl_cacheNever).build();
	}
	
	@GET
	@Path("/openChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public SuspendResponse<String> openChannel(
				final @QueryParam("channelID") String channelID, 
				final @QueryParam("outputComments") @DefaultValue("false") Boolean outputComments)
			throws Exception {
		
		if (channelID == null || channelID.isEmpty())
			throw new Exception("channelID must not be null or empty");
		
		final NotificationBroadcaster broadCaster = notificationMgr.getBroadcaster(channelID, true);
		
		final SuspendResponseBuilder<String> suspendResponseBuilder = new SuspendResponse.SuspendResponseBuilder<String>()
			.period(1, TimeUnit.MINUTES)
		  .addListener(new AtmosphereResourceEventListener() {
				
				public void onSuspend(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' suspended");
					
					// touch channel session to (keep alive)
					HttpSessionMgr.getInstance().getSession(channelID, false);
					
					broadCaster.setResumed(false);
					
					// process queued notifications: re-check if broadcaster was absent (because of all resources were disconnected)
					notificationMgr.processQueue(channelID, broadCaster);
				}
				
				public void onResume(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' resumed");
					
					broadCaster.setResumed(true);
				}
				
				public void onDisconnect(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					// NOTE: disconnect also happens in "connected"-environments, e.g. on suspend timeout (after 5 minutes)
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' disconnected");
					
					// TODO [haed]: this is uncommented for testing, maybe a "buggy" destroy kills all subscriptions
					// 		-> if this works, we do not need a scheduled destroy on suspend, we can use onDisconnect
					//			 (but should should also wait until destroy channel, because of reconnects on shortly network failures)
//					// destroy channel
//					HttpSessionMgr.getInstance().destroySession(channelID);
				}
				
				public void onBroadcast(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
				}

				public void onThrowable(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					logger.fatal("error in atmosphere", event.throwable());
        }
			})
			.broadcaster(broadCaster);
		
		// configure and disable caching
		suspendResponseBuilder.outputComments(outputComments);
		suspendResponseBuilder.cacheControl(cacheControl_cacheNever);
		
	  return suspendResponseBuilder.build();
	}
	
	@GET
	@Path("/ping")
	public Response ping(
				final @QueryParam("channelID") String channelID)
			throws Exception {
		
		notificationMgr.sendData(
				notificationMgr.getBroadcaster(channelID, true), 
				notificationMgr.buildNotificationCtnr("haed.notification.ping." + channelID, "ping"));
		
		return Response.ok().build();
	}
}