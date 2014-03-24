package haed.notification;

import javax.servlet.http.HttpServletRequest;

public interface ChannelAdapter {
  
  String getChannelID(final HttpServletRequest httpServletRequest)
      throws Exception;
}