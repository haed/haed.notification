package haed.notification;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterCache;

public class NotificationBroadcasterCache implements BroadcasterCache<HttpServletRequest, HttpServletResponse> {
	
	private static final Logger logger = Logger.getLogger(NotificationBroadcasterCache.class);
	
	
//	private LinkedList<Object> cache = null;
	private StringBuilder s = null;
	
	public NotificationBroadcasterCache() {
	}
	
	public void start() {
		if (logger.isDebugEnabled())
			logger.debug("start notification broadcaster cache, hashCode: " + hashCode());
	}
	
  public void stop() {
  	if (logger.isDebugEnabled())
			logger.debug("stop notification broadcaster cache, hashCode: " + hashCode());
  }
  
	public void addToCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> resource, final Object element) {
		
		if (resource == null) {
			
			if (logger.isDebugEnabled())
				logger.debug("add to cache: " + element);
			
			synchronized (this) {
					
					if (s == null)
						s = new StringBuilder();
					
					s.append((String) element);
					
//				if (cache == null)
//					cache = new LinkedList<Object>();
//				
//				cache.add(element);
      }
		}
  }

	public List<Object> retrieveFromCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> resource) {
		
		if (s == null)
			return Collections.EMPTY_LIST;
		
		final List<Object> l = new LinkedList<Object>();
		synchronized (this) {
			l.add(s.toString());
			s = null;
		}
		
		if (logger.isInfoEnabled())
			logger.info("retrieve from cache: " + l.get(0));
		
		return l;
		
//		if (cache == null)
//			return Collections.EMPTY_LIST;
//		
//		final List<Object> l;
//		synchronized (this) {
//			l = cache;
//			cache = null;
//		}
//		
//		if (logger.isInfoEnabled())
//			logger.info("retrieve " + l.size() + " elements from cache");
//		
//		return l;
  }
}