package name.kevinross.jsonrpc;

public class Meta {
	public int id;
	public String name;
    public String package_;
	public String toString() {
		return String.format("<Meta package=%s name=%s id=%d>", package_, name, id);
	}
}
