package name.kevinross.jsonrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.lang.reflect.Proxy;
import java.util.LinkedList;


public class ApiClient extends RpcClient {
    public static ApiClient global_client = null;

    public ApiClient(String base_endpoint) {
        super(base_endpoint);
        if (global_client == null) {
            global_client = this;
        }
    }

    public ApiClient(String base_endpoint, String endpoint) {
        super(base_endpoint, endpoint);
    }

    public static RootInterface Root() {
        return (RootInterface) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{RootInterface.class}, new ProxyHandler(global_client));
    }

    public static RootInterface Root(String base_endpoint) {
        return (RootInterface) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{RootInterface.class}, new ProxyHandler(new ApiClient(base_endpoint)));
    }

    public static <ApiClass extends PythonMagic> ApiClass API(Class<ApiClass> clazz) {
        if (global_client == null)
            throw new RuntimeException("must have created at least one ApiClient first!");
        return ((ApiClient) global_client.get("api")).proxy(clazz);
    }

    public static GlobalsInterface Globals() {
        if (global_client == null)
            throw new RuntimeException("must have created at least one ApiClient first!");
        return ((ApiClient) global_client.get("globals")).proxy(GlobalsInterface.class);
    }

    public static void main(String[] args) {
        ApiClient a = new ApiClient("http://localhost:9055/api");
        RootInterface r = Root("http://localhost:9055/api");
        GlobalsInterface g = Globals();
        ApiClient b = (ApiClient) a.get("api");
        C c = b.<C>proxy(C.class);
        System.out.println(g.echo("hello"));
    }

    public <ApiClass extends PythonMagic> ApiClass proxy(Class<ApiClass> c) {
        return (ApiClass) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{c}, new ProxyHandler<ApiClass>(this));
    }

    public Object[] __marshall_args__(Object... oargs) {
        Object[] args = super.__marshall_args__(oargs);
        LinkedList<Object> argsList = new LinkedList<Object>();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ApiClient) {
                argsList.add(String.format("hash:%s", ((ApiClient) args[i]).interface_().hash));
            } else if (args[i] instanceof Resolvable) {
                ObjectRef<Resolvable> ref = new ObjectRef<Resolvable>();
                ref.one = true;
                ref.sqlref = new SqlRef();
                Resolvable obj = (Resolvable) args[i];
                ref.sqlref.name = obj.__meta__.name;
                ref.sqlref.items = new int[1];
                ref.sqlref.items[0] = obj.__meta__.id;
                argsList.add(ref);
            } else {
                argsList.add(args[i]);
            }
        }
        return argsList.toArray();
    }

    public Object __resolve_references__(String resp) {
        Resolvable objs[] = null;
        JsonParser parser = new JsonParser();
        JsonElement jsonobj = parser.parse(resp);
        boolean isarray = jsonobj.isJsonArray();
        if (isarray) {
            objs = gson.fromJson(resp, Resolvable[].class);
        } else {
            objs = new Resolvable[1];
            objs[0] = gson.fromJson(resp, Resolvable.class);
        }
        LinkedList<Resolvable> objects = new LinkedList<Resolvable>();
        for (int i = 0; i < objs.length; i++) {
            Resolvable obj = objs[i];
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Resolvable> klass = (Class<? extends Resolvable>) Class.forName(obj.__meta__.package_ + obj.__meta__.name);
                if (isarray) {
                    objects.add(gson.fromJson(jsonobj.getAsJsonArray().get(i), klass));
                } else {
                    objects.add(gson.fromJson(jsonobj, klass));
                }
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (objects.size() == 1) {
            return objects.get(0);
        } else {
            return objects;
        }
    }

    interface C extends PythonMagic {
        public void a();
    }
}
