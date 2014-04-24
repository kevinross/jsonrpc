jsonrpc
=======

my own JSONRPCv2 implementation

Usage
=====
Creating a server API is easy! Simply subclass JSONRPC and create properties and methods per your own requirements. After that, build_routes() needs to be called with an instance of your subclass as the first argument (optionally, a second argument of a lambda can be used to provide a session to api calls).

Any subclass of JSONRPC, once instantiated either by the server or indirectly by a client, will be a remote object clients can interact with. In other words, suppose you wished to return an interface that represented an object in a database and that you wished to have callable functions, all you would need to do is create a class as previously described and return an instance of it from a function in a previously declared class.

Example Server:

    class Car(JSONRPC):
      def __init__(self, id):
        super(Car, self).__init__(self)
        self.id = id
      def move(self, new_x, new_y):
        db.update(self.id, ('x', 'y'), [new_x, new_y]
      def cur_x(self):
        return db.select(Car.id==self.id).x
      def cur_y(self):
        return db.select(Car.id==self.id).y
      
    class Road(JSONRPC):
      def first_car(self):
        return Car(0)
      def remove_car(self, c):  # the serialization layer translates JSONRPC clients to a form suitable for clients, who can then pass them back to the server
        db.delete(Car.id==c.id)
      def echo(self, val):
        return val
      
    build_routes(Road())
  
Example Client:

    client = APIClient('http://localhost:9055/api')
    c = client.first_car()
    c.move(100, 100)
    client.remove_car(c)
      
One can also use the batch functionality of JSONRPCv2 as each APIClient is also a context manager!

    with client.first_car() as car:
      car.move(100, 100)
      car.move(car.val(car.cur_x())+50, 200)  # calling .val(obj) causes the passed object to be evaluated before the batch is executed
    car.__results__ == [0, None, None] # note how the usual semantics break; the first element is expected to be 100 but .val forces evaluation before the first call to .move
    # results are .val calls, in order, followed by the regular calls

Java
====
You can either use the ApiClient as is and use casting or you can specify an interface and a proxy will be returned.

Example for the former:

    ApiClient c = new ApiClient("http://localhost:9055/api");
    ApiClient car = (ApiClient)c.call("first_car");
    int x = (Integer)car.call("cur_x");  // cur_x is a function on the remote object
    int id = (Integer)car.get("id");  // id is a property though
  
Example for the latter:

    interface Car { 
      public int id();
      public int cur_x();
      public int cur_y();
      public void move(int x, int y);
    }
    Car car = c.call("first_car").<Car>proxy(Car.class);
    car.move(1, 1);
    car.move(10, 1);
  
And we can do batch calls in java too!

    List<Object> results = c.batch(new BatchCallRunnable() {
      @Override
      public void run(BatchClient r) {
        r.val(r.call("first_car")).move(1, 100);  // have to get the value from the .call before  using it
        r.call("echo", "hello world");
      }
    }
    // results now contains [ApiClient instance, null, "hello world"] for the calls to first_car and echo
    // results are .val calls, in order, followed by regular function calls
I have yet to test proxy support for batch calls, it *should* work though as it ultimately ends up using the "call" function that batch calls use

JavaScript
==========
Similar to python, except the batch support is implemented slightly differently due to the lack of context managers in javascript.

    var client = jsonrpc("/api");
    client.echo("hello world");
    var results = client.batch(function(r) {
      r.echo("hello world");
      r.val(r.first_car()).move(100, 100); // same as in Java, gotta get the value first
      r.echo("hello " + r.val(r.echo("world")));
    }
    results == [JSONRPCCLIENT instance, "world", "hello world", null, "hello world"]
    // results are .val calls, in order, followed by regular function calls
