package haed.notification;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterCache;

// TODO: check the following scenario: broadcaster gets destroyed and re-created
public class SerialBroadcasterCache implements BroadcasterCache<HttpServletRequest, HttpServletResponse> {
  
  private static final Logger logger = Logger.getLogger(SerialBroadcasterCache.class);
  
  
  
  
//  static final String HEADER = HeaderConfig.X_CACHE_DATE;
  static final String HEADER = "X-Cache-Serial";
  
  private static final Set<SerialBroadcasterCache> caches = 
    Collections.synchronizedSet(new HashSet<SerialBroadcasterCache>());
  
  static void cleanUp(long timeout) {
    
    if (logger.isDebugEnabled())
      logger.debug("clean up all serial broadcaster caches");
    
    // TODO: optimize synchronization block
    synchronized (caches) {
      for (final SerialBroadcasterCache cache: caches)
        cache._cleanUp(timeout);
    }
  }
  
  static void register(final SerialBroadcasterCache cache) {
    caches.add(cache);
  }
  
  static void unregister(final SerialBroadcasterCache cache) {
    caches.remove(cache);
  }
  
  
  static Long parseSerial(final HttpServletRequest r) {
    
    String v = r.getHeader(HEADER);
    if (v == null) {
      Object attr = r.getAttribute(HEADER);
      if (attr != null)
        v = attr.toString();
    }
    
    if (v == null)
      return null;
    
    try {
      return Long.parseLong(v.trim());
    } catch (final NumberFormatException e) {
      logger.fatal("invalid " + HEADER + " value: " + v);
      return null;
    }
  }
  
  
  private final AtomicLong serial = new AtomicLong(0);
  
  
  CachedMessage head = new CachedMessage(null, 0L, null);
  CachedMessage tail = head;
  
  // TODO: clean up strategy
  final Map<Long, CachedMessage> cache = Collections.synchronizedMap(new HashMap<Long, CachedMessage>());
  
  @Override
  public void start() {
    
    if (logger.isDebugEnabled())
      logger.debug("start");
    
    register(this);
  }
  
  @Override
  public void stop() {
    
    if (logger.isDebugEnabled())
      logger.debug("stop");
    
    unregister(this);
  }
  
  Long getActualSerial() {
    return serial.get();
  }
  
  CachedMessage _addToCache(final Object e) {
    
    final CachedMessage cm = new CachedMessage(serial.incrementAndGet(), e, null);
//    if (tail == null) {
//      head.next = tail = cm;
//    } else
      tail = tail.next = cm;
    
    cache.put(cm.serial, cm);
    
    if (logger.isDebugEnabled())
      logger.debug("addToCache: " + cm);
    
    return cm;
  }
  
  @Override
  public void addToCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> r, final Object e) {
    
//    if (e instanceof List) {
//      throw new RuntimeException("???");
//    }
    
    
    synchronized (serial) {
      
      _addToCache(e);
      
//      if (r == null) {
//        _addToCache(e);
//      } else {
//        
//        Long s = parseSerial(r.getRequest());
//        if (s == null) {
//          
//          if (logger.isDebugEnabled())
//            logger.fatal("no serial found");
//          
//          final CachedMessage cm = _addToCache(e);
//          r.getResponse().setHeader(HEADER, "" + cm.serial);
//          
//        } else {
//          
//          if (s.longValue() != this.serial.get())
//            logger.warn("found invalid serial: " + s + " (@request) != " + this.serial + " (actual)");
//        }
//      }
    }
  }
  
  @Override
  public List<Object> retrieveFromCache(final AtmosphereResource<HttpServletRequest, HttpServletResponse> r) {
    
    final Long s = parseSerial(r.getRequest());
    
    CachedMessage cm;
    if (s == null) {
      logger.warn("no " + HEADER + " set, fall back to tail");
      cm = tail;
    } else {
      
      cm = cache.get(s);
      if (cm == null) {
        
        // lost message?
        logger.warn("found no message for serial id: " + s + ", lowest cached id: " + head.serial);
        
        // start with head
        cm = head;
      }
    }
    
    final List<Object> l = new LinkedList<Object>();
    while (cm.next != null) {
      cm = cm.next;
      l.add(cm.message);
    }
    
    if (logger.isDebugEnabled())
      logger.debug("retrieveFromCache, serial: " + cm.serial + ", elementCount: " + l.size() + " ,r: " + r);
    
    // set header if there where some cached entries
    if (l.isEmpty() == false)
      r.getResponse().setHeader(HEADER, "" + cm.serial);
    
    return l;
  }
  
  void _cleanUp(final long timeout) {
    
    if (logger.isDebugEnabled())
      logger.debug("starting clean up serial broadcaster cache (" + toString() + "), elements before: " + cache.size());
    
    long ts = System.currentTimeMillis() - timeout;
    
    while (head.next != null && head.timestamp < ts) {
      synchronized (serial) {
        cache.remove(head.serial);
        head = head.next;
      }
    }
    
    if (logger.isDebugEnabled())
      logger.debug("finished clean up serial broadcaster cache (" + toString() + "), elements after: " + cache.size());
  }
}