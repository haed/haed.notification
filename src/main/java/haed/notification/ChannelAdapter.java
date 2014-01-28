package haed.notification;

import com.sun.jersey.api.core.HttpContext;

public interface ChannelAdapter {
  
  String getChannelID(final HttpContext httpContext)
      throws Exception;
}