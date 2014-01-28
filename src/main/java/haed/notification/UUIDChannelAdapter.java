package haed.notification;

import java.util.UUID;

import com.sun.jersey.api.core.HttpContext;

public class UUIDChannelAdapter implements ChannelAdapter {
  
  @Override
  public String getChannelID(final HttpContext httpContext) {
    return UUID.randomUUID().toString();
  }
}