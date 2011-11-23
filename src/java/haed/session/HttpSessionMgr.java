package haed.session;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

public final class HttpSessionMgr {
	
	private static final Logger logger = Logger.getLogger(HttpSessionMgr.class);
	
	private static final HttpSessionMgr instance = new HttpSessionMgr();
	
	public static HttpSessionMgr getInstance() {
		return instance;
	}
	
	private static final int lastAccessedThreshold = 1000 * 30; // 30 sec
	
	private final Map<String, HttpSession> httpSessions = new HashMap<String, HttpSession>();
	
	private final TreeMap<Long, List<HttpSession>> httpSessionsByLastAccessedTime = new TreeMap<Long, List<HttpSession>>();
	
	private long currentLastAccessedTime = System.currentTimeMillis();
	private List<HttpSession> currentSessions = new LinkedList<HttpSession>();
	
	private final List<HttpSessionListener> httpSessionListeners = new LinkedList<HttpSessionListener>();
	
	private HttpSessionMgr() {
	}
	
	private void fireSessionCreatedEvent(HttpSession httpSession) {
		final HttpSessionEvent event = new HttpSessionEvent(httpSession);
		synchronized (httpSessionListeners) {
			for (HttpSessionListener listener: httpSessionListeners)
				listener.sessionCreated(event);
		}
	}
	
	private void fireSessionDestroyedEvent(HttpSession httpSession) {
		final HttpSessionEvent event = new HttpSessionEvent(httpSession);
		synchronized (httpSessionListeners) {
			for (HttpSessionListener listener: httpSessionListeners)
				listener.sessionDestroyed(event);
		}
	}
	
	public void addSessionListener(HttpSessionListener httpSessionListener) {
		synchronized (httpSessionListeners) {
			httpSessionListeners.add(httpSessionListener);
		}
	}
	
	public void removeSessionListener(HttpSessionListener httpSessionListener) {
		synchronized (httpSessionListeners) {
			httpSessionListeners.remove(httpSessionListener);
		}
	}
	
	private HttpSession createSession(String id) {
		
		HttpSession httpSession = new HttpSession(id);
		httpSessions.put(httpSession.getId(), httpSession); 
		
		// store at current sessions
		httpSession.setLastAccessedTime(currentLastAccessedTime);
		currentSessions.add(httpSession);
		
		// log
		if (logger.isDebugEnabled())
			logger.debug("created new http session with id: " + httpSession.getId());
		
		fireSessionCreatedEvent(httpSession);
		
		return httpSession;
	}
	
	public HttpSession createSession() {
		return createSession(HttpSessionUtil.generateSessionId());
	}
	
	public HttpSession getSession(String id, boolean create) {
		HttpSession httpSession = httpSessions.get(id);
		if (httpSession == null) {
			
			if (create) {
				synchronized (httpSessions) {
					httpSession = httpSessions.get(id);
					if (httpSession == null)
						httpSession = createSession(id);
				}
			}
		} else {
			
			checkCurrentSessions();
			
			// update last accessed time
			if (httpSession.getLastAccessedTime() < currentLastAccessedTime) {
				
				// update map
				List<HttpSession> list = httpSessionsByLastAccessedTime.get(httpSession.getLastAccessedTime());
				if (list != null) {
					synchronized (list) {
						list.remove(httpSession);
					}
				}
				
				synchronized (currentSessions) {
					currentSessions.add(httpSession);
				}
				
				// set last accessed time
				httpSession.setLastAccessedTime(currentLastAccessedTime);
			}
		}
		
		return httpSession;
	}
	
	public void destroySession(String id) {
		
		HttpSession httpSession = httpSessions.remove(id);
		
		if (httpSession != null) {
			
			List<HttpSession> list = httpSessionsByLastAccessedTime.get(httpSession.getLastAccessedTime());
			if (list != null) {
				synchronized (list) {
					list.remove(httpSession);
				}
			}
			
			// log
			if (logger.isDebugEnabled())
				logger.debug("destroyed http session with id: " + httpSession.getId());
			
			fireSessionDestroyedEvent(httpSession);
		}
	}
	
	private void checkCurrentSessions() {
		long now = System.currentTimeMillis();
		if (now - currentLastAccessedTime > lastAccessedThreshold) {
			httpSessionsByLastAccessedTime.put(currentLastAccessedTime, currentSessions);
			currentSessions = new LinkedList<HttpSession>();
			currentLastAccessedTime = now;
		}
	}
	
	public void checkTimeout(long timeInMs) {
		
		checkCurrentSessions();
		
		long now = System.currentTimeMillis();
		while (!httpSessionsByLastAccessedTime.isEmpty() && (now - httpSessionsByLastAccessedTime.firstKey() > timeInMs)) {
			for (HttpSession httpSession: httpSessionsByLastAccessedTime.pollFirstEntry().getValue()) {
				
				// log
				if (logger.isDebugEnabled())
					logger.debug("http session with id '" + httpSession.getId() + "' timed out");
				
				destroySession(httpSession.getId());
			}
		}
		
		if (logger.isDebugEnabled())
			logger.debug("after HttSessionMgr#checkTimeout(): " + httpSessions.size() + " http sessions alive (stored in " + httpSessionsByLastAccessedTime.size() + " lastAccessedTime-containers)");
	}
}