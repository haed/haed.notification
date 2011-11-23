package haed.session;

public class HttpSessionEvent {
	
	private final HttpSession httpSession;
	
	public HttpSessionEvent(HttpSession httpSession) {
		this.httpSession = httpSession;
	}
	
	public HttpSession getSession() {
		return httpSession;
	}
}