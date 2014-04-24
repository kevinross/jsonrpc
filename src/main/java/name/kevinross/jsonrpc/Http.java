package name.kevinross.jsonrpc;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.protocol.HttpContext;

public class Http {
	private static HttpInterface iface = new DefaultHttp();
    public static void setIface(HttpInterface _iface) {
        iface = _iface;
    }
	public static HttpClient client() {
		return iface.client();
	}
	public static HttpContext context() {
		return iface.context();
	}
	public static CookieStore jar() {
		return iface.jar();
	}
}
