package name.kevinross.jsonrpc;

import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class DefaultHttp implements HttpInterface {
	private static HttpClient client_;
	private static HttpContext context_;
	private static CookieStore jar_;
	private static ClientConnectionManager cm_;
	private static HttpParams params_;
	static {
		params_ = new BasicHttpParams();
		ConnManagerParams.setMaxTotalConnections(params_, 100);
        HttpProtocolParams.setVersion(params_, HttpVersion.HTTP_1_1);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(
                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        cm_ = new ThreadSafeClientConnManager(params_, schemeRegistry);
	}
	public HttpClient client() {
		if (client_ == null) {
			context();
			client_ = new DefaultHttpClient(cm_, params_);
		}
		return client_;
	}
	public HttpContext context() {
		if (context_ == null) {
			context_ = new BasicHttpContext();
			context_.setAttribute(ClientContext.COOKIE_STORE, jar());
		}
		return context_;
	}
	public CookieStore jar() {
		if (jar_ == null)
			jar_ = new BasicCookieStore();
		return jar_;
	}
}
