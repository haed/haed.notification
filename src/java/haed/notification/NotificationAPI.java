package haed.notification;

import haed.session.HttpSessionMgr;

import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListener;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.jersey.SuspendResponse;
import org.atmosphere.jersey.SuspendResponse.SuspendResponseBuilder;
import org.codehaus.jettison.json.JSONObject;

import com.google.gson.Gson;

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
		return Response.ok(notificationMgr.createChannelId()).cacheControl(cacheControl_cacheNever).build();
	}
	
	@GET
	@Path("/openChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public SuspendResponse<String> openChannel(
				final @QueryParam("channelID") String channelIDParameter, 
				final @QueryParam("outputComments") String outputCommentsParameter)
			throws Exception {
		
		if (channelIDParameter == null || channelIDParameter.isEmpty())
			throw new Exception("channelID must not be null or empty");
		
		final Gson gson = new Gson();
		final String channelID = gson.fromJson(channelIDParameter, String.class);
		
		final Broadcaster broadCaster = notificationMgr.getBroadcaster(channelID, true);
		
		final SuspendResponseBuilder<String> suspendResponseBuilder = new SuspendResponse.SuspendResponseBuilder<String>()
			.period(1, TimeUnit.MINUTES)
		  .addListener(new AtmosphereResourceEventListener() {
				
				public void onSuspend(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' suspended");
					
					
					// touch channel session to (keep alive)
					HttpSessionMgr.getInstance().getSession(channelID, false);
					
//					// process queued notifications: re-check if broadcaster was absent (because of all resources were disconnected)
//					notificationMgr.processQueue(channelID, broadCaster);
				}
				
				public void onResume(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' resumed");
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
		
		if (outputCommentsParameter == null)
			suspendResponseBuilder.outputComments(false);
		else
			suspendResponseBuilder.outputComments(gson.fromJson(outputCommentsParameter, Boolean.class).booleanValue());
		
		// disable caching
		suspendResponseBuilder.cacheControl(cacheControl_cacheNever);
		
	  return suspendResponseBuilder.build();
	}
	
	@Path("/ping")
	@POST
	public Response ping(
				final @FormParam("channelID") String channelIDParameter, 
				final @FormParam("message") String messageParameter)
			throws Exception {
		
		// TODO [haed]: what for a response should be used on failure (e.g. no channel or no broadcaster)?
		
		final Gson gson = new Gson();
		final String channelID = channelIDParameter == null ? null : gson.fromJson(channelIDParameter, String.class);
		if (channelID != null) {
			
			final Broadcaster broadcaster = notificationMgr.getBroadcaster(channelID, false);
			if (broadcaster != null) {
				
				final String notificationType = "haed.notification.ping." + channelID;
				final JSONObject message = new JSONObject(messageParameter);
				
				notificationMgr.sendData(broadcaster, notificationMgr.buildNotificationCtnr(notificationType, message));
			}
		}
		
		return Response.ok().build();
	}
}