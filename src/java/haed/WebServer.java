package haed;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class WebServer {
	
	public static File getRootDirectory()
			throws UnsupportedEncodingException {
		
		final String classesPath = URLDecoder.decode(WebServer.class.getResource("/").getPath(), "UTF-8");
		return new File(classesPath).getParentFile().getParentFile().getParentFile();
	}
	
	
	public static void main(final String[] arguments)
			throws Exception {
		
		startWebServer().join();
  }
	
	
	public static Server startWebServer()
			throws Exception {
		
		final File rootDirectory = getRootDirectory();
		
		final WebAppContext context = new WebAppContext();
		context.setDescriptor(rootDirectory + "/webroot/WEB-INF/web.xml");
		context.setResourceBase(rootDirectory + "/webroot");
		context.setContextPath("/");
		context.setParentLoaderPriority(true);
		
		final Server server = new Server(8080);
		server.setHandler(context);
		
		server.start();
		
		return server;
	}
}