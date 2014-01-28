package haed.notification;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
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

import com.sun.jersey.api.core.HttpContext;

@Path("/")
public class NotificationAPI {
	
	private static final Logger logger = Logger.getLogger(NotificationAPI.class);
	
	
	public static final CacheControl cacheControl_cacheNever = new CacheControl();
	
	static {
		cacheControl_cacheNever.setMaxAge(0);
		cacheControl_cacheNever.setNoCache(true);
	}
	
	
	public static URI parseURI(String uri) {
    
    if (uri == null)
      return null;
    
    uri = uri.trim();
    if (uri.startsWith("http") == false) // only parse uris with a 'http(s)' scheme
      return null;
    
    // cut hash
    final int idx = uri.indexOf("#");
    if (idx > -1)
      uri = uri.substring(0, idx);
    
    try {
      return new URI(uri);
    } catch (final Throwable t) {
      logger.fatal("error on checking referrer", t);
      return null;
    }
  }
	
	
	private static final String headers = 
	  "X-Atmosphere-Framework, X-Atmosphere-tracking-id, X-Atmosphere-Transport, X-Cache-Date";
//	  "X-Atmosphere-Framework, X-Atmosphere-tracking-id, X-Atmosphere-Transport, X-Cache-Date, " + SerialBroadcasterCache.HEADER;
	
	
	static void enableCORS(final HttpServletRequest request, final HttpServletResponse response)
	    throws Exception {
    
    String allowOrigin = "*";
    try {
      final String referrerHeader = request.getHeader("referer");
      if (referrerHeader != null && referrerHeader.trim().isEmpty() == false) {
        final URI referrerURI = parseURI(referrerHeader);
        allowOrigin = referrerURI.getScheme() + "://" + referrerURI.getAuthority();
      }
    } catch (final Throwable t) {
      logger.fatal("error on checking referrer", t);
    }
    
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Headers", headers);
    response.setHeader("Access-Control-Allow-Origin", allowOrigin);
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    response.setHeader("Access-Control-Expose-Headers", headers);
	}
	
	public static String createPingNotificationType(final String channelID) {
    return "haed.notification.ping." + channelID;
  }
	
	
	@OPTIONS
  @Path("/createChannel")
  public void createChannel_OPTIONS(
        final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response)
      throws Exception {
    enableCORS(request, response);
  }
	
	@GET
	@Path("/createChannel")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response createChannel_GET(
	      final @Context HttpContext httpContext,
	      final @Context HttpServletRequest request,
	      final @Context HttpServletResponse response)
			throws Exception {
	  
	  enableCORS(request, response);
	  
	  final NotificationMgrImpl notificationMgr = NotificationMgrImpl.getInstance();
		
		final String channelID = notificationMgr.getChannelAdapter().getChannelID(httpContext);
		notificationMgr.getBroadcaster(channelID, true);
		
		// also register for ping and ping initial
		final String pingNotificationType = createPingNotificationType(channelID);
		notificationMgr.subscribe(channelID, pingNotificationType);
		notificationMgr.sendNotification(pingNotificationType, Boolean.TRUE);
		
		return Response.ok(channelID).cacheControl(cacheControl_cacheNever).build();
	}
	
	@OPTIONS
  @Path("/openChannel")
  public void openChannel_OPTIONS(
        final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response)
      throws Exception {
    enableCORS(request, response);
  }
	
	@GET
	@Path("/openChannel")
	public SuspendResponse<String> openChannel_GET(
        final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response, 
        final @QueryParam("channelID") String channelID, 
        final @QueryParam("outputComments") @DefaultValue("false") Boolean outputComments)
      throws Exception {
	  
	  
	  enableCORS(request, response);
    
    
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
	
	
	@OPTIONS
  @Path("/ping")
  public void ping_OPTIONS(
        final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response)
      throws Exception {
    enableCORS(request, response);
  }
	
	@GET
	@Path("/ping")
	public Response ping_GET(
  	    final @Context HttpServletRequest request, 
        final @Context HttpServletResponse response, 
				final @QueryParam("channelID") String channelID)
			throws Exception {
	  
	  enableCORS(request, response);
		
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
	
	
	static String debug(final HttpServletRequest httpServletRequest) {
    
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