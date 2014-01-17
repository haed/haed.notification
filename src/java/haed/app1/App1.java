package haed.app1;

import haed.notification.NotificationMgrImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
public class App1 {
	
	@GET
	@Path("/subscribe")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response subscribe(
				final @QueryParam("channelID") String channelID, 
				final @QueryParam("topic") String topic)
			throws Exception {
		NotificationMgrImpl.getInstance().subscribe(channelID, topic);
		return Response.ok().build();
	}
	
	@GET
	@Path("/send")
	@Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
	public Response send(
				final @QueryParam("topic") String topic, 
				final @QueryParam("message") String message)
			throws Exception {
	  NotificationMgrImpl.getInstance().sendNotification(topic, message);
		return Response.ok().build();
	}
}