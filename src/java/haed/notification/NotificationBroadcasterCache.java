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
	
	
	private List<Object> cache = null;
	
	public NotificationBroadcasterCache() {
	}
	
	public void start() {
	}
	
  public void stop() {
  }
  
	public void addToCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> resource, final Object element) {
		
		if (resource == null) {
			
			// should not happen, log
			logger.warn("add to cache (should not happen): " + element);
			
			synchronized (this) {
				
				if (cache == null)
					cache = new LinkedList<Object>();
				
				cache.add(element);
			}
		}
  }

	public List<Object> retrieveFromCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> resource) {
		
		if (cache == null)
			return Collections.EMPTY_LIST;
		
		final List<Object> l;
		synchronized (this) {
			l = cache;
			cache = null;
		}
		
		if (logger.isDebugEnabled())
			logger.info("retrieve " + l.size() + " elements from cache");
		
		return l;
  }
}