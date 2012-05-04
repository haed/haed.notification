package haed.notification;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterCache;

// TODO: check the following scenario: broadcaster gets destroyed and re-created
public class SerialBroadcasterCache implements BroadcasterCache {
  
  private static final Logger logger = Logger.getLogger(SerialBroadcasterCache.class);
  
  
  //static final String HEADER = "X-Cache-Serial";
  static final String HEADER = "Expires";
  
  
  
  static void register(final SerialBroadcasterCache cache) {
  }
  
  static void unregister(final SerialBroadcasterCache cache) {
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
  
  
  CachedMessage head = new CachedMessage(serial.get(), null, null);
  CachedMessage tail = head;
  
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
    tail = tail.next = cm;
    
    cache.put(cm.serial, cm);
    
    if (logger.isDebugEnabled())
      logger.debug("addToCache: " + cm);
    
    return cm;
  }
  
  @Override
  public void addToCache(final AtmosphereResource r, final Object e) {
    
    synchronized (serial) {
      
      if (r != null) {
        final Long s = parseSerial(r.getRequest());
        if (s == null)
          r.getResponse().setHeader(SerialBroadcasterCache.HEADER, "" + tail.serial);
        else
          r.getResponse().setHeader(SerialBroadcasterCache.HEADER, "" + (s.longValue() + 1));
      }
    }
  }
  
  @Override
  public List<Object> retrieveFromCache(final AtmosphereResource r) {
    
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
      if (cm.message != null)
        l.add(cm.message);
    }
    
    if (logger.isDebugEnabled())
      logger.debug("retrieveFromCache, serial: " + cm.serial + ", elementCount: " + l.size() + ", r: " + r);
    
    // set header if there where some cached entries
//    if (l.isEmpty() == false)
      r.getResponse().setHeader(HEADER, "" + cm.serial);
    
    return l;
  }
  
  void cleanUp(final Long serial) {
    
    if (logger.isDebugEnabled())
      logger.debug("starting clean up serial broadcaster cache to serial " + serial);
    
    while (head.next != null && head.serial.longValue() < serial.longValue()) {
      synchronized (this.serial) {
        cache.remove(head.serial);
        head = head.next;
      }
    }
    
    if (logger.isDebugEnabled())
      logger.debug("finished clean up serial broadcaster cache (" + toString() + "), elements after: " + cache.size());
  }
}