package haed.notification;

import java.util.UUID;

public class DefaultNotificationAdapter implements NotificationAdapter {

  @Override
  public String createChannelID() {
    return UUID.randomUUID().toString();
  }
}