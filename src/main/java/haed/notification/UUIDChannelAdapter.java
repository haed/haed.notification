package haed.notification;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

public class UUIDChannelAdapter implements ChannelAdapter {
  
  @Override
  public String getChannelID(final HttpServletRequest httpServletRequest) {
    return UUID.randomUUID().toString();
  }
}