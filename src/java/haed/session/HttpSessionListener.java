package haed.session;

public interface HttpSessionListener {
	
	void sessionCreated(HttpSessionEvent httpSessionEvent);
	
	void sessionDestroyed(HttpSessionEvent httpSessionEvent);
}