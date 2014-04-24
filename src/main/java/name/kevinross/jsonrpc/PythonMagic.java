package name.kevinross.jsonrpc;

/**
 * Created by Kevin Ross on 2014-04-22.
 */
public interface PythonMagic {
    public String __doc__();
    public String __hash__();
    public Object __getattr__(String attr);
    public Object __setattr__(String attr, Object val);
    public String __repr__();
    public String __str__();
}
