package name.kevinross.jsonrpc;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.protocol.HttpContext;

public interface HttpInterface {
	public abstract HttpClient client();
	public HttpContext context();
	public CookieStore jar();
}
