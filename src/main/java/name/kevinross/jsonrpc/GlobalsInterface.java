package name.kevinross.jsonrpc;

import java.util.List;

/**
 * Created by Kevin Ross on 2014-04-22.
 */
public interface GlobalsInterface extends PythonMagic {
    public List<PythonMagic> allobjects();
    public PythonMagic getobject(Object key);
    public String echo(String s);
    public Object getattr(String obj, String attr);
    public void setattr(String obj, String attr, Object val);
}
