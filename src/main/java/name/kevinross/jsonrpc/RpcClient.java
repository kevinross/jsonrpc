package name.kevinross.jsonrpc;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public abstract class RpcClient {
	protected String base_endpoint = null;
	public String endpoint = null;
	private HashMap<String, HashMap<ArrayWrapper, Object>> cache = null;
	public Interface interface__ = null;
    public boolean can_connect = false;
	protected Gson gson = new GsonBuilder().serializeNulls().registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();
	public RpcClient(String base_endpoint) {
		this(base_endpoint, null);
	}
	public RpcClient(String base_endpoint, String endpoint) {
		this.base_endpoint = base_endpoint;
		this.endpoint = base_endpoint + ((endpoint != null)?("/" + endpoint):"");
        cache = new HashMap<String, HashMap<ArrayWrapper, Object>>();
        can_connect = canConnect();
	}
    public Interface interface_() {
        if (this.interface__ == null)
            this.interface__ = (Interface) __rpccall__(new FunctionCall("__interface__"));
        return this.interface__;
    }
    public void load_cache(FileInputStream fi) {
        try {
            ObjectInputStream oi = new ObjectInputStream(fi);
            cache = (HashMap<String, HashMap<ArrayWrapper, Object>>) oi.readObject();
            interface__ = null;
        } catch (Exception ex) {

        }
    }
    public void save_cache(FileOutputStream fo) {
        try {
            ObjectOutputStream os = new ObjectOutputStream(fo);
            os.writeObject(cache);
        } catch (Exception ex) {

        }
    }
	public Object[] __marshall_args__(Object... args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof BatchResultRunnable) {
                BatchResultRunnable r = (BatchResultRunnable)args[i];
                FunctionCall call =  r.target.batch.get((Integer)r.run());
                args[i] = r.target.__rpccall__(r.target.endpoint, call);
                r.target.results.add(args[i]);
            }
        }
        return args;
    }
	public abstract Object __resolve_references__(String obj);
	public Object __parse_response__(String resp) throws RemoteException {
		JsonParser parser = new JsonParser();
		JsonElement main_response = (JsonElement) parser.parse(resp);
        if (main_response.isJsonArray()) {
            List<Object> res = new Vector<Object>();
            for (Object o : main_response.getAsJsonArray()) {
                res.add(__parse_response__(gson.toJson(o)));
            }
            return res;
        }
        JsonObject response = main_response.getAsJsonObject();
        if (response.has("error")) {
            JsonObject e = response.get("error").getAsJsonObject().get("data").getAsJsonObject();
            throw new RemoteException(e.get("exception").getAsString(), e.get("message").getAsString());
        }
		JsonElement value = response.get("result");
		String valuestr = gson.toJson(response.get("result"));
		if (valuestr.contains("hash:")) {
			Class<? extends RpcClient> klass = this.getClass();
			try {
				Constructor<? extends RpcClient> m = klass.getDeclaredConstructor(String.class, String.class);
				String new_endpoint = gson.fromJson(value, String.class);
				return m.newInstance(this.base_endpoint, new_endpoint.replace("hash:", ""));
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		} else if (valuestr.contains("funcs")) {
			return gson.fromJson(valuestr, Interface.class);
		}
		if (value.isJsonPrimitive()) {
			JsonPrimitive val = value.getAsJsonPrimitive();
			if (val.isBoolean()) {
				return val.getAsBoolean();
			} else if (val.isNumber()) {
				return val.getAsNumber();
			} else if (val.isString()) {
				DateTimeFormatter dtparser = ISODateTimeFormat.dateHourMinuteSecond();
				try {
					return dtparser.parseDateTime(val.getAsString());
				} catch (Exception ex) {
				}
				return val.getAsString();
			} else {
				return null;
			}
		} else if (value.isJsonNull()) {
			return null;
		} else if (value.isJsonObject() && !value.getAsJsonObject().has("__meta__")) {
			return gson.fromJson(value, HashMap.class);
		} else if (value.isJsonArray()) {
			if (value.getAsJsonArray().size() == 0)
				return new LinkedList();
			JsonElement obj = value.getAsJsonArray().get(0);
			if (obj.isJsonObject()) {
				if (!obj.getAsJsonObject().has("__meta__")) {
					return gson.fromJson(value, LinkedList.class);
				}
			}
		}
		return __resolve_references__(valuestr);
	}
	public void flush() {
		cache.clear();
	}
	private void set(String endpoint, FunctionCall func, Object result) {
		HashMap<ArrayWrapper, Object> inner = new HashMap<ArrayWrapper, Object>();
		inner.put(new ArrayWrapper(func.params), result);
		cache.put(endpoint + '/' + func.method, inner);
	}
	private boolean has(String endpoint, FunctionCall func) {
		if (!cache.containsKey(endpoint + '/' + func.method))
			return false;
		return cache.containsKey(endpoint + '/' + func.method) && cache.get(endpoint + '/' + func.method).containsKey(new ArrayWrapper(func.params));
	}
	private Object get(String endpoint, FunctionCall func) {
		return cache.get(endpoint + '/' + func.method).get(new ArrayWrapper(func.params));
	}
	public Object __rpccall__(FunctionCall func) {
		return __rpccall__(this.endpoint, func, true);
	}
	public Object __rpccall__(String endpoint, Object func) {
		return __rpccall__(endpoint, func, true);
	}
	public Object __rpccall__nocache(FunctionCall func) {
		return __rpccall__(this.endpoint, func, false);
	}
	public Object __rpccall__nocache(String endpoint, FunctionCall func) {
		return __rpccall__(endpoint, func, false);
	}
    private boolean canConnect() {
        try {
            URI host = new URI(this.endpoint);
            InetAddress addy = InetAddress.getByName(host.getHost());
            return addy.isReachable(2);
        } catch (Exception ex) {
            return false;
        }
    }
	public synchronized Object __rpccall__(String endpoint, Object funcobj, boolean usecache) {
        try {
            // normal case is passing a functioncall, batch case is passing a List<FunctionCall>
            FunctionCall func = (FunctionCall)funcobj;
            if (usecache && has(endpoint, func))
                return get(endpoint, func);
            if (!can_connect)
                if (has(endpoint, func)) {
                    return get(endpoint, func);
                } else {
                    return null;
                }
            func.params = __marshall_args__(func.params);
            funcobj = func;
        } catch (ClassCastException ex) {
            try {
                BatchClient b = (BatchClient) funcobj;
                List<FunctionCall> newcalls = new Vector<FunctionCall>();
                for (int i = 0; i < b.batch.size(); i++) {
                    b.batch.get(i).params = __marshall_args__(b.batch.get(i).params);
                }
                for (FunctionCall f : b.batch) {
                    if (!b.called.contains(f)) {
                        newcalls.add(f);
                    }
                }
                funcobj = newcalls;
            } catch (ClassCastException ex2) {

            }
        }
		HttpPost post = new HttpPost(endpoint);
		StringEntity req = null;
		req = new StringEntity(
					gson.toJson(funcobj),
					"UTF-8"
					);
		post.setEntity(req);
		post.setHeader(HTTP.CONTENT_TYPE, "application/json");
		HttpResponse resp = null;
		HttpClient client = Http.client();
		try {
			resp = client.execute(post, Http.context());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		Object ret = null;
		HttpEntity rep = resp.getEntity();
		InputStreamReader is = null; 
		try {
			is = new InputStreamReader(rep.getContent());
			BufferedReader rd = new BufferedReader(is);
			String out = "";
			String line = null;
			while ((line = rd.readLine()) != null) {
				out += line;
			}
			resp.getEntity().consumeContent();
			ret = __parse_response__(out);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException();
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
        FunctionCall func = (FunctionCall)funcobj;
        if (usecache) set(endpoint, func, ret);
		return ret;
	}
	public Object call(String func, Object... args) {
		return __rpccall__(this.endpoint, new FunctionCall(func, args));
	}
    public List<Object> batch(BatchCallRunnable b) {
        // preamble
        BatchClient bc = new BatchClient(this);
        // run
        b.run(bc);
        // postamble
        bc.results.clear();
        bc.results.addAll((List<Object>)this.__rpccall__(this.endpoint, bc, false));
        return bc.results;
    }
	public Object call_nocache(String func, Object... args) {
		return __rpccall__(this.endpoint, new FunctionCall(func, args), false);
	}
	public Object get(String attr) {
		return __rpccall__(this.base_endpoint,
                new FunctionCall("globals.getattr",
                        String.format("hash:%d", this.interface_().hash),
                        attr));
	}
	public Object get_nocache(String attr) {
		return __rpccall__(this.base_endpoint, 
				new FunctionCall("globals.getattr", 
						String.format("hash:%d",this.interface_().hash),
						attr), false);
	}
	public void set(String attr, Object value) {
		__rpccall__(this.base_endpoint, new FunctionCall("globals.setattr", attr, value));
	}
	private class DateTimeTypeConverter implements JsonSerializer<DateTime>, JsonDeserializer<DateTime> {
		  // No need for an InstanceCreator since DateTime provides a no-args constructor
		  @Override
		  public JsonElement serialize(DateTime src, Type srcType, JsonSerializationContext context) {
		    return new JsonPrimitive(src.toString());
		  }
		  @Override
		  public DateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context)
		      throws JsonParseException {
		    return new DateTime(json.getAsString());
		  }
		}
}
