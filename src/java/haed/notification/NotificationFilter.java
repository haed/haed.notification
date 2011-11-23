package haed.notification;

public interface NotificationFilter {
	
	boolean isValid(Object notification);
}