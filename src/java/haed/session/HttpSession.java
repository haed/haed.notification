package haed.session;

import java.util.HashMap;
import java.util.Map;

public final class HttpSession {
	
	private final String id;
	
	private long creationTime = System.currentTimeMillis();
	private long lastAccessedTime = creationTime;
	
	private final Map<String, Object> attributes = new HashMap<String, Object>();
	
	public HttpSession(String id) {
		this.id = id;
	}
	
	public Object getAttribute(String name) {
		return this.attributes.get(name);
	}
	
	public long getCreationTime() {
		return this.creationTime;
	}
	
	public long getLastAccessedTime() {
		return this.lastAccessedTime;
	}
	
	protected void setLastAccessedTime(long lastAccessedTime) {
		this.lastAccessedTime = lastAccessedTime;
	}
	
	public String getId() {
		return this.id;
	}
	
	public void setAttribute(String name, Object value) {
		this.attributes.put(name, value);
	}
}