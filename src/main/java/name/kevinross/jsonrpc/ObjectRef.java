package name.kevinross.jsonrpc;

import java.util.LinkedList;

public class ObjectRef<T extends Resolvable> {
	public SqlRef sqlref;
	boolean one;
	public String toString() {
		return String.format("<ObjectRef sqlref=%s>", sqlref);
	}
	@SuppressWarnings("unchecked")
	T object() {
		FunctionCall func = new FunctionCall("api.database.get", sqlref.name, sqlref.items);
		return (T) ApiClient.global_client.__rpccall__(ApiClient.global_client.base_endpoint, func);
	}
	@SuppressWarnings("unchecked")
	LinkedList<T> objects() {
		FunctionCall func = new FunctionCall("api.database.get", sqlref.name, sqlref.items);
		return (LinkedList<T>)ApiClient.global_client.__rpccall__(ApiClient.global_client.base_endpoint, func);
	}
	
}
