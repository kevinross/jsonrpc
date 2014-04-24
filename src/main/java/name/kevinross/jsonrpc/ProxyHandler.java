package name.kevinross.jsonrpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Created by Kevin Ross on 2014-04-22.
 */
public class ProxyHandler <ApiType extends PythonMagic> implements InvocationHandler {
    private ApiClient client;
    private Class api_iface;
    public ProxyHandler(ApiClient c) {
        client = c;
    }
    public ProxyHandler(ApiClient c, Class api_interface) {
        client = c;
        api_iface = api_interface;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = null;
        Class cl = method.getReturnType();
        Class[] pcl = cl.getInterfaces();
        if (Arrays.asList(client.interface_().attrs).contains(method.getName()))
            result = client.get(method.getName());
        else
            result = client.call(method.getName(), args);
        if (result instanceof ApiClient) {
            ApiClient c = (ApiClient)result;
            if (method.getName().contentEquals("globals"))
                return Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{GlobalsInterface.class}, new ProxyHandler(c));
            else if (method.getName().contentEquals("api"))
                return Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{api_iface}, new ProxyHandler<ApiType>(c));
        }
        return result;
    }
}
