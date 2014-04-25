var prototype = require('prototype');
var RemoteException = prototype.Class.create({
                                       initialize: function (exc, message) {
                                           this.exc = exc;
                                           this.message = message;
                                       },
                                       toString: function () {
                                           return this.exc + ': ' + this.message;
                                       }
                                   });
Array.prototype.extend = function (other_array) {
    /* you should include a test to check whether other_array really is an array */
    if (other_array instanceof Array)
        other_array.forEach(function(v) {this.push(v)}, this);
}
Array.prototype.contains = function (val) {
    return this.indexOf(val) >= 0;
}
function isString(o) {
    return o != null && (typeof o == "string" || (typeof o == "object" && o.constructor === String));
}
function callable(obj) {
 return !!(obj && obj.constructor && obj.call && obj.apply);
}
function Date_clone(obj) {
  var copy = new Date();
  copy.setTime(obj.getTime());
  return copy;
}

Number_clone =
Boolean_clone =
String_clone = function(obj) {
  return obj;
}
function Object_clone(obj) {
  if(obj.cloneNode) return obj.cloneNode(true);
  var copy = obj instanceof Array ? [] : {};
  for(var attr in obj) {
    var d = Object.getOwnPropertyDescriptor(obj, attr);
      if (d) {
          Object.defineProperty(copy, attr, d);
          continue;
      }
    if(typeof obj[attr] == "function" || obj[attr]==null || !obj[attr].clone)
      copy[attr] = obj[attr];
    else if(this[attr]==obj) copy[attr] = copy;
    else {
        if (obj[attr] instanceof Date) copy[attr] = Date_clone
        else {
            copy[attr] = String_clone(obj[attr]);
        }
    }
  }
  return copy;
}



var base_endpoint = "/api";
var global_api = null;
var object_cache = {};
var global_id = 0;

function handle_error(response) {
    if ('error' in response) {
        if (response.error.code < 0) {
            switch (response.error.code) {
                case -32600:
                    throw new Error('JSONInvalidRequest');
                    break;
                case -32700:
                    throw new Error('JSONParseError');
                    break;
                case -32601:
                    throw new Error('JSONMethodNotFoundError');
                    break;
                case -32602:
                    throw new Error('JSONInvalidParams');
                    break;
                case -32603:
                    throw new Error('JSONInternalError');
                    break;
                case -32000:
                    throw new Error('JSONServerError');
                    break;
            }
        } else {
            throw new RemoteException(response.error.exception, response.error.message);
        }
    }
}

var JSONRPCBASE = Class.create({
                                   initialize: function (endpoint) {
                                       this.__endpoint__ = endpoint;
                                       this.__interface__ = this.__rpccall__(endpoint, "__interface__");
                                       this.__hash__ = this.__interface__.hash;
                                       this.__class__ = this.__interface__.name;
                                   },
                                   toString: function () {
                                       return '<' + this.__class__ + '@' + this.__hash__ + ' __endpoint__="' + this.__endpoint__ + '">';
                                   },
                                   toJSON: function () {
                                       return 'hash:' + this.__hash__;
                                   },
                                   val: function(v) {
                                       return this.__marshall_args__.apply(this, [v]);
                                   },
                                   __parse_response__: function (data) {
                                       var pre = JSON.parse(data);
                                       if (Array.isArray(pre)) {
                                           var ret = [];
                                           for (var i = 0; i < pre.length; i++) {
                                               ret.push(
                                                   this.__parse_response__(Object.toJSON(pre[i]))
                                               );
                                           }
                                           return ret;
                                       }
                                       var result = JSON.parse(data, function (key, val) {
                                           if (isString(val) && val.indexOf("hash:") == 0) {
                                               return jsonrpc("/api/" + val.replace("hash:", ""));
                                           }
                                           if (key == 'sqlref') {
                                               this.objects = function () {
                                                   return global_api.api.resolve_sqlobjs(val.name, val.items);
                                               };
                                               if (val.items.length == 1) {
                                                   this.object = function () {
                                                       return global_api.api.resolve_sqlobjs(val.name, val.items)[0];
                                                   };
                                               }
                                               return;
                                           }
                                           var iso = /(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+)|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d)|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d)/;
                                           if (iso.match(val)) {
                                               return new Date(val);
                                           }
                                           return val;
                                       });
                                       handle_error(result);
                                       return result.result;
                                   },
                                   __marshall_args__: function () {
                                       var args = Array.prototype.slice.call(arguments, 0);
                                       for (var i = 0; i < args.length; i++) {
                                           if (callable(args[i])) {
                                               if (!this.__called__.contains(args[i]())) {
                                                   this.__called__.push(args[i]());
                                                   args[i] = this.__docall__(this.__endpoint__, args[i]());
                                                   this.__results__.push(args[i]);
                                               } else {
                                                   args[i] = this.__results__[this.__called__.indexOf(args[i]())];
                                               }
                                           }
                                           if (args[i] instanceof Object && '__interface__' in args[i]) {
                                               args[i] = 'hash:' + args[i].__hash__;
                                           }
                                           if (args[i] instanceof Object && '__meta__' in args[i]) {
                                               args[i] = {
                                                   one: true,
                                                   sqlref: {
                                                       name: args[i].__meta__.name,
                                                       items: [args[i].__meta__.id]
                                                   }
                                               };
                                           }
                                       }
                                       return args;
                                   },
                                   __async_rpccall__: function (url, func) {
                                       var args = this.__marshall_args__.apply(this, Array.prototype.slice.call(arguments, 2));
                                       var cb = args.pop();
                                       var params = {
                                           id: global_id++,
                                           jsonrpc: "2.0",
                                           method: func,
                                           params: args
                                       }
                                       new Ajax.Request(url, {
                                           asynchronous: true,
                                           contentType: 'application/json',
                                           method: 'post',
                                           parameters: Object.toJSON(params),
                                           evalJS: 'force',
                                           requestHeaders: ['Cookie', document.cookie],
                                           onSuccess: function (response) {
                                               var data = this.__parse_response__(response.responseText);
                                               if (cb != null) {
                                                   cb(data);
                                               }
                                           }
                                       });
                                   },
                                   __rpccall__: function (url, func) {
                                       var params = null;
                                       if (func instanceof Object && '__endpoint__' in func) {
                                           out = [];
                                           for (var i = 0; i < func.__batch__.length; i++) {
                                               func.__batch__[i]['params'] = func.__marshall_args__.apply(func, func.__batch__[i]['params']);
                                           }
                                           for (var i = 0; i < func.__batch__.length; i++) {
                                               if (!func.__called__.contains(func.__batch__[i])) {
                                                   out.push(func.__batch__[i]);
                                               }
                                           }
                                           params =  out;
                                       } else {
                                           var args = this.__marshall_args__.apply(this, Array.prototype.slice.call(arguments, 2));
                                           params = {
                                               id: global_id++,
                                               jsonrpc: "2.0",
                                               method: func,
                                               params: args
                                           }
                                       }
                                       if ('__batch__' in this) {
                                           this.__batch__.push(params);
                                           var i = this.__batch__.length - 1;
                                           var that = this;
                                           return (function() {
                                               return function() {
                                                   return that.__results__.length < i ? that.__results__[i] : params;
                                               }
                                           })();
                                       }
                                       return this.__docall__(url, params);
                                   },
                                   __docall__: function(url, params) {
                                       return this.__parse_response__(new Ajax.Request(url, {
                                           asynchronous: false,
                                           contentType: 'application/json',
                                           method: 'post',
                                           parameters: Object.toJSON(params),
                                           evalJS: 'force',
                                           requestHeaders: ['Cookie', document.cookie]
                                       }).transport.responseText);
                                   },
                                   /* usage:
                                    var result = null;
                                    api.batch(function(obj) {
                                    obj.call();
                                    result = obj.echo(obj.echo("hello"));
                                    });
                                    // result is a callable now
                                    print(result());
                                    */
                                   batch: function (cb) {
                                       // enter
                                       this.enter_batch();
                                       // body
                                       cb(this.__batch_client__);
                                       // exit
                                       return this.exit_batch();
                                   },
                                   enter_batch: function() {
                                       // enter
                                       var c = Object_clone(this);
                                       c.__batch__ = [];
                                       c.__results__ = [];
                                       c.__called__ = []
                                       this.__batch_client__ = c;
                                       return c;
                                   },
                                   exit_batch: function() {
                                       var c = this.__batch_client__;
                                       var results = this.__rpccall__(c.__endpoint__, c);
                                       c.__results__.extend(results);
                                       return c.__results__;

                                   }
                               })

function jsonrpc(endpoint) {
    var API = Class.create(JSONRPCBASE);
    var result = new API(endpoint);
    var obj = {};
    for (var i = 0; i < result.__interface__.funcs.length; i++) {
        obj[result.__interface__.funcs[i]] = result.__rpccall__.curry(result.__endpoint__, result.__interface__.funcs[i]);
        obj[result.__interface__.funcs[i]].async = result.__async_rpccall__.curry(result.__endpoint__, result.__interface__.funcs[i]);
    }
    API.addMethods(obj);
    for (var i = 0; i < result.__interface__.attrs.length; i++) {
        (function () {
            var attr = result.__interface__.attrs[i];
            Object.defineProperty(result, attr, {
                get: function () {
                    // cache RPC objects only
                    var cache_key = result.__hash__ + '_' + attr;
                    if (cache_key in object_cache) {
                        return object_cache[cache_key];
                    }
                    var val = result.__rpccall__(base_endpoint, "globals.getattr", result, attr);
                    if (val instanceof Object && '__interface__' in val) {
                        object_cache[cache_key] = val;
                    }
                    return val;
                },
                set: function (value) {
                    result.__rpccall__(base_endpoint, "globals.setattr", result, attr, value);
                },
                configurable: true,
                enumerable: true
            });
        })();
    }
    return result;
}
global_api = null;
api = null;
globals = null;
function GLOBAL_API() {
    global_api = global_api || jsonrpc("/api");
    return global_api;
}
function API() {
    api = api || GLOBAL_API().api;
    return api;
}
function GLOBALS() {
    globals = globals || GLOBAL_API().globals;
    return globals;
}

