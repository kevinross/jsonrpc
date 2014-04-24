package name.kevinross.jsonrpc;

public class SqlRef {
	String name;
	public int[] items;
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<SqlRef name=");
		sb.append(name);
		sb.append(" items=[");
		for (int i = 0; i < items.length; i++) {
			sb.append(items[i]);
			if (i != items.length - 1)
				sb.append(",");
		}
		sb.append("]>");
		return sb.toString();
	}
}
