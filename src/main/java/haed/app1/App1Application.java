package haed.app1;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class App1Application extends Application {
	
	private final Set<Object> singletons = new HashSet<>();

	public App1Application() {
		singletons.add(new App1());
	}
	
	@Override
	public Set<Object> getSingletons() {
	  return singletons;
  }
}