package name.kevinross.jsonrpc;

import java.util.List;
import java.util.Vector;

/**
 * Created by r0ssar00 on 2014-04-19.
 */
public class BatchClient extends ApiClient {
    public final List<FunctionCall> batch = new Vector<FunctionCall>();
    public final List<Object> results = new Vector<Object>();
    public final List<FunctionCall> called = new Vector<FunctionCall>();

    RpcClient client;
    public BatchClient(RpcClient c) {
        super(c.endpoint);
        client = c;
    }

    public Object val(BatchResultRunnable r) {
        FunctionCall f = this.batch.get((Integer)r.run());
        return this.__rpccall__(f);
    }
    @Override
    public BatchResultRunnable call(String func, Object... args) {
        final FunctionCall f = new FunctionCall(func, args);
        batch.add(f);
        final int i = batch.size() - 1;
        return new BatchResultRunnable(this) {
            @Override
            public Object run() {
                BatchClient.this.called.add(f);
                if (results.size() > 0) {
                    return target.results.get(i);
                } else {
                    return new Integer(i);
                }
            }
        };
    }
}
