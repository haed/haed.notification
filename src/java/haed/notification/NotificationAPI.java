package haed.notification;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListener;
import org.atmosphere.jersey.SuspendResponse;
import org.atmosphere.jersey.SuspendResponse.SuspendResponseBuilder;
import org.eclipse.jetty.http.HttpStatus;

@Path("/")
public class NotificationAPI {
	
	private static final Logger logger = Logger.getLogger(NotificationAPI.class);
	
	public static final CacheControl cacheControl_cacheNever = new CacheControl();
	
	static {
		cacheControl_cacheNever.setMaxAge(0);
		cacheControl_cacheNever.setNoCache(true);
	}
	
	@GET
	@Path("/createChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response createChannel()
			throws Exception {
		
		final String channelID = UUID.randomUUID().toString();
		
		final NotificationMgr notificationMgr = NotificationMgr.getInstance();
		notificationMgr.getBroadcaster(channelID, true);
		
		// also register for ping and ping initial
		final String pingNotificationType = createPingNotificationType(channelID);
		notificationMgr.subscribe(channelID, pingNotificationType);
		notificationMgr.sendNotification(pingNotificationType, "ping");
		
		return Response.ok(channelID).cacheControl(cacheControl_cacheNever).build();
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
		
		NotificationBroadcaster broadCaster = NotificationMgr.getInstance().getBroadcaster(channelID, false);
		if (broadCaster == null) {
			
			if (logger.isDebugEnabled())
				logger.debug("no channel found for id '" + channelID + "'");
			
			throw new WebApplicationException(HttpStatus.NOT_FOUND_404);
	  
		} else {
			
			// check if channel is already connected
			// => GitHub issue: https://github.com/Atmosphere/atmosphere/issues/87
			if (broadCaster.getAtmosphereResources().isEmpty() == false) {
				
				logger.warn("channel already connected, channelID: " + channelID);
				
//				// avoid duplicate channel with exception
//				throw new WebApplicationException(HttpStatus.BAD_REQUEST_400);
			}
		}
		
		final NotificationBroadcaster _broadCaster = broadCaster;
		final SuspendResponseBuilder<String> suspendResponseBuilder = new SuspendResponse.SuspendResponseBuilder<String>()
		
			.period(1, TimeUnit.MINUTES)
//		  .period(20, TimeUnit.SECONDS) // stress test setting
		
		  .addListener(new AtmosphereResourceEventListener() {
				
				public void onSuspend(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' suspended");
					
				  _broadCaster.processCache();
				}
				
				public void onResume(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
					if (logger.isDebugEnabled())
						logger.debug("channel with id '" + channelID + "' resumed");
				}
				
				public void onDisconnect(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					
				  // TODO [haed]: re-check this
					// NOTE: disconnect also happens in "connected"-environments, e.g. on suspend timeout (after 5 minutes)
					
					if (logger.isDebugEnabled())
						logger.info("channel with id '" + channelID + "' disconnected");
				}
				
				public void onBroadcast(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
				}

				public void onThrowable(final AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) {
					logger.fatal("error in atmosphere", event.throwable());
        }
			})
			.broadcaster(broadCaster);
		
		// configure and disable caching
		suspendResponseBuilder
			.outputComments(outputComments)
			.cacheControl(cacheControl_cacheNever);
		
	  return suspendResponseBuilder.build();
	}
	
	@GET
	@Path("/ping")
	public Response ping(
				final @QueryParam("channelID") String channelID)
			throws Exception {
		
		final NotificationMgr notificationMgr = NotificationMgr.getInstance();
		
		// check if channel exists
		final NotificationBroadcaster broadcaster = notificationMgr.getBroadcaster(channelID, false);
		if (broadcaster == null) {
			
			if (logger.isDebugEnabled())
				logger.debug("no channel found for id '" + channelID + "'");
			
			throw new WebApplicationException(HttpStatus.NOT_FOUND_404);
		}
		
		// send ping
		notificationMgr.sendNotification(createPingNotificationType(channelID), "ping");
		
		return Response.ok().build();
	}
	
	public static String createPingNotificationType(final String channelID) {
		return "haed.notification.ping." + channelID;
	}
}