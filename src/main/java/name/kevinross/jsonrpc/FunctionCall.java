package name.kevinross.jsonrpc;

public class FunctionCall {
    public static int global_id = 0;
	public String method;
	public Object[] params;
    public int id = global_id++;
    public final String jsonrpc = "2.0";
	public FunctionCall() {
		method = "";
        params = new Object[]{};
	}
	public FunctionCall(String method, Object... args) {
		this.method = method;
		this.params = args;
	}
	public String toString() {
		String val = method + "(";
		for (Object i : params) {
			val += i.toString() + ", ";
		}
		val+= ")";
		return val.replace(", )", ")");
	}
    public boolean equals(FunctionCall other) {
        return this.id == other.id;
    }
}
