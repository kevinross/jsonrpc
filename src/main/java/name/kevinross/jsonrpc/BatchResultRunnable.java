package name.kevinross.jsonrpc;

/**
 * Created by r0ssar00 on 2014-04-19.
 */
public abstract class BatchResultRunnable {
    public BatchClient target;
    public BatchResultRunnable(BatchClient tgt) {target=tgt;}
    public abstract Object run();
}
