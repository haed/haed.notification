package haed.notification;

import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.jersey.SuspendResponse;
import org.atmosphere.jersey.SuspendResponse.SuspendResponseBuilder;

@Path("/")
public class NotificationAPI {
	
	private static final Logger logger = Logger.getLogger(NotificationAPI.class);
	
	
	public static final CacheControl cacheControl_cacheNever = new CacheControl();
	
	static {
		cacheControl_cacheNever.setMaxAge(0);
		cacheControl_cacheNever.setNoCache(true);
	}
	
	public static String createPingNotificationType(final String channelID) {
    return "haed.notification.ping." + channelID;
  }
	
	@GET
	@Path("/createChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response createChannel_GET(
	      final @Context HttpServletRequest request,
	      final @Context HttpServletResponse response)
			throws Exception {
	  
	  final NotificationMgrImpl notificationMgr = NotificationMgrImpl.getInstance();
		
		final String channelID = notificationMgr.getChannelAdapter().getChannelID(request);
		notificationMgr.getBroadcaster(channelID, true);
		
		// also register for ping and ping initial
		final String pingNotificationType = createPingNotificationType(channelID);
		notificationMgr.subscribe(channelID, pingNotificationType);
		notificationMgr.sendNotification(pingNotificationType, Boolean.TRUE);
		
		return Response.ok(channelID).cacheControl(cacheControl_cacheNever).build();
	}
	
	/**
	 * A special fake endpoint for a different ws-url (for proxies).
	 */
	@GET
	@Path("/channels/{channelID}/open.ws")
  public SuspendResponse<String> openWebSocketChannel_GET(
        final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response, 
        final @PathParam("channelID") String channelID, 
        final @QueryParam("outputComments") @DefaultValue("false") Boolean outputComments)
      throws Exception {
	  return openChannel_GET(request, response, channelID, outputComments);
	}
	
	@Deprecated
	@GET
	@Path("/openChannel")
	public SuspendResponse<String> openChannel_GET_deprecated(
  	    final @Context HttpServletRequest request,
  	    final @Context HttpServletResponse response,
  	    final @QueryParam("channelID") String channelID,
  	    final @QueryParam("outputComments") @DefaultValue("false") Boolean outputComments)
	    throws Exception {
	  return openChannel_GET(request, response, channelID, outputComments);
	}
	
	@GET
	@Path("/channels/{channelID}/open")
	public SuspendResponse<String> openChannel_GET(
        final @Context HttpServletRequest request,
        final @Context HttpServletResponse response,
        final @PathParam("channelID") String channelID,
        final @QueryParam("outputComments") @DefaultValue("false") Boolean outputComments)
      throws Exception {
	  
    // HOTFIX: to prevent long-polling calls to be pipe-lined
    // (HTTP Pipelining is used be all mobile browser and can also be activated in desktop browsers)
    response.setHeader("Connection", "close");
    
    
    if (channelID == null || channelID.isEmpty())
      throw new Exception("channelID must not be null or empty");
    
    final NotificationMgrImpl notificationMgr = NotificationMgrImpl.getInstance();
    
    Broadcaster broadCaster = notificationMgr.getBroadcaster(channelID, false);
    if (broadCaster == null) {
      
      if (logger.isDebugEnabled())
        logger.debug("no channel found for id '" + channelID + "'" + debug(request));
      
      throw new WebApplicationException(404);
      
    } else {
      
      // check if channel is already connected
      // => GitHub issue: https://github.com/Atmosphere/atmosphere/issues/87
      if (broadCaster.getAtmosphereResources().isEmpty() == false)
        logger.warn("channel already connected, channelID: " + channelID + debug(request));
    }
    
    final SuspendResponseBuilder<String> suspendResponseBuilder = new SuspendResponse.SuspendResponseBuilder<String>()
      .period(2, TimeUnit.MINUTES)
      .broadcaster(broadCaster);
    
    // configure and disable caching
    suspendResponseBuilder
      .outputComments(outputComments.booleanValue())
      .cacheControl(cacheControl_cacheNever);
    
    return suspendResponseBuilder.build();
	}
	
	@GET
	@Path("/ping")
	public Response ping_GET(
  	    final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response, 
				final @QueryParam("channelID") String channelID)
			throws Exception {
		
		// check if channel exists
	  final NotificationMgrImpl notificationMgr = NotificationMgrImpl.getInstance();
	  
		final Broadcaster broadcaster = notificationMgr.getBroadcaster(channelID, false);
		if (broadcaster == null) {
			
			if (logger.isDebugEnabled())
				logger.debug("no channel found for id '" + channelID + "'");
			
			throw new WebApplicationException(404);
		}
		
		// send ping
		notificationMgr.sendNotification(createPingNotificationType(channelID), Boolean.TRUE);
		
		return Response.ok().cacheControl(cacheControl_cacheNever).build();
	}
	
	
	public static String debug(final HttpServletRequest httpServletRequest) {
    
    final StringBuilder debug = new StringBuilder();
    
    final String referer = httpServletRequest.getHeader("referer");
    if (referer != null)
      debug.append(", referer=").append(referer);
    
    final String userAgent = httpServletRequest.getHeader(HttpHeaders.USER_AGENT);
    if (userAgent != null)
      debug.append(", user-agent=").append(userAgent);
    
    final String remoteHost = httpServletRequest.getRemoteHost();
    if (remoteHost != null)
      debug.append(", remoteHost=").append(remoteHost);
    
    final String remoteAddr = httpServletRequest.getRemoteAddr();
    if (remoteAddr != null)
      debug.append(", remoteAddr=").append(remoteAddr);
    
    return debug.toString();
  }
}