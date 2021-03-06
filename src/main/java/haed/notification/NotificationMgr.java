package haed.notification;

import com.google.gson.GsonBuilder;

public interface NotificationMgr {
  
  void setGsonBuilder(GsonBuilder gsonBuilder);
  
  void setChannelAdapter(ChannelAdapter channelAdapter);
  
  
  ChannelAdapter getChannelAdapter();
  
  void sendNotification(String notificationType, Object source)
      throws Exception;
  
  void subscribe(String channelID, String notificationType)
      throws Exception;
}