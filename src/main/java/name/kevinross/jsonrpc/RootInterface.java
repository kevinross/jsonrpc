package name.kevinross.jsonrpc;

/**
 * Created by Kevin Ross on 2014-04-22.
 */
public interface RootInterface extends PythonMagic {
    public GlobalsInterface globals();
    public PythonMagic api();
}
