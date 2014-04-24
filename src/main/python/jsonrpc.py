import bottle, simplejson, requests, functools, re, datetime
from bottle import route, request, SimpleTemplate
from functools import wraps
from copy import deepcopy as copy
from threading import local
bottle.debug(True)

date_re = r"(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+)|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d)|(\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d)"

def json(obj):
	if hasattr(obj, '__json__'):
		return obj.__json__()
	if 'dict' in dir(obj):
		return obj.dict
	if '__rhash__' in dir(obj):
		return obj.__rhash__
	if isinstance(obj, list):
		return [json(x) for x in obj]
	if isinstance(obj, dict):
		return {x:json(obj[x]) for x in obj}
	if isinstance(obj, datetime.datetime):
		return obj.isoformat()
	return obj

def unjson(obj):
	if isinstance(obj, list):
		return [unjson(x) for x in obj]
	if isinstance(obj, dict):
		return {x:unjson(obj[x]) for x in obj}
	if isinstance(obj, basestring) and obj.startswith('hash:'):
		global objects
		return objects[int(obj.strip('hash:'))]
	return obj

def interface(obj):
	if hasattr(obj, '__interface__'):
		return obj.__interface__()
	return None

def resolve(obj, path):
	res = obj
	path = str(path)
	for comp in path.split('.'):
		res = getattr(res, comp)
	return res

def jsonify(func):
	@wraps(func)
	def jsoned(*args, **kwargs):
		return json(func(*args, **kwargs))
	return jsoned

objects = {}
def getobjects():
	global objects
	return objects.keys()

class JSONError(Exception):
	def __init__(self, exc):
		self.exc = exc
	@property
	def dict(self):
		return dict(
			id=request.json.get('id'),
			jsonrpc="2.0",
			error=dict(
				code = self.json_rpc_code,
				message = self.__class__.__name__ + ': ' + self.message,
				data = dict(
					exception=self.exc.__class__.__name__,
					message=self.exc.message
				)
			)
		)

class JSONInvalidRequest(JSONError):
	json_rpc_code = -32600

class JSONParseError(JSONError):
	json_rpc_code = -32700

class JSONMethodNotFound(JSONError):
	json_rpc_code = -32601

class JSONInvalidParams(JSONError):
	json_rpc_code = -32602

class JSONInternalError(JSONError):
	json_rpc_code = -32603

class JSONServerError(JSONError):
	json_rpc_code = -32000

class JSONRPC(object):
	def __init__(self, *args, **kwargs):
		global objects
		objects[hash(self)] = self
	@property
	def dict(self):
		return ':'.join(['hash', str(hash(self))])
	def __interface__(self):
		return dict(
			name = self.__class__.__name__,
			hash = hash(self),
			attrs = [x for x in dir(self) if not callable(getattr(self, x)) and x not in ('__interface__', '__jsoncall__')],
			funcs = [x for x in dir(self) if callable(getattr(self, x)) and x not in ('__interface__', '__jsoncall__', '__jsoncall_one__', '__class__', 'make_result', 'make_error')]
		)
	def make_result(self, json, val):
		return dict(
			id=json.get('id'),
			jsonrpc="2.0",
			result=val
		)
	def make_error(self, code, msg, exc):
		return dict(
			id=request.json.get('id'),
			jsonrpc="2.0",
			error=dict(
				code=code,
				message=msg,
				data=dict(
					exception=exc.__class__.__name__,
					message=exc.message
				)
			)
		)
	@jsonify
	def __jsoncall__(self, sess=lambda:None):
		try:
			json = bottle.json_lds(request.body.read(request.MEMFILE_MAX))
		except ValueError, e:
			return JSONParseError(e)
		if isinstance(json, list):
			res = []
			for x in json:
				if 'id' in x:
					res.append(self.__jsoncall_one__(x))
				else:
					self.__jsoncall_one__(x)
			return res
		else:
			return self.__jsoncall_one__(json)
	@jsonify
	def __jsoncall_one__(self, body):
		func = None
		json = body
		if 'jsonrpc' not in json:
			return JSONInvalidRequest(Exception('no version specified'))
		if 'method' not in json:
			return JSONInvalidRequest(Exception('no method specified'))

		args = unjson(json.get('params', []))
		if args.__class__ not in (list, dict):
			return JSONInvalidParams(Exception('args not an object or array'))
		try:
			func = resolve(self, json['method'])
		except AttributeError, e:
			return JSONMethodNotFound(e)
		try:
			if isinstance(args, list):
				v = func(*args)
			elif isinstance(args, dict):
				v = func(**args)
			else:
				return JSONInternalError(Exception('how???'))
		except AttributeError, e:
			return JSONMethodNotFound(e)
		except Exception, ex:
			return self.make_error(1, 'application exception', ex)

		return self.make_result(json, v)
	__getattr__ = object.__getattribute__
	__setattr__ = object.__setattr__

class Globals(JSONRPC):
	@staticmethod
	def allobjects():
		return getobjects()
	@staticmethod
	def getobject(key):
		global objects
		return objects[key]
	@staticmethod
	def echo(val):
		return val
	@staticmethod
	def getattr(obj, attr):
		return json(resolve(obj, attr))
	@staticmethod
	def setattr(obj, attr, val):
		if '.' not in attr:
			setattr(obj, attr, val)
			return
		obj = resolve(obj, '.'.join(attr.split('.')[0:-1]))
		setattr(obj, attr.split('.')[-1], val)
	# don't want people (re-)defining things here
	def __setattr__(self, key, value):
		raise AttributeError("can't set attribute")
class APIObj(JSONRPC):
	pass
class API(JSONRPC):
	def __init__(self, api):
		super(API, self).__init__()
		object.__setattr__(self, "globals", Globals())
		object.__setattr__(self, "api", api)
	# so I can reference objects by hash if needed
	def __getattr__(self, item):
		global objects
		try:
			item = int(item)
		except:
			pass
		if item in objects.keys():
			return objects[item]
		return object.__getattribute__(self, item)
	# don't want people (re-)defining things here
	def __setattr__(self, key, value):
		raise AttributeError("can't set attribute")

class RemoteException(Exception):
	def __init__(self, exc, msg):
		self.exc = exc
		self.msg = msg
	def __str__(self):
		return '%s: %s' % (self.exc, self.msg)
def datetime_decoder(d):
    if isinstance(d, list):
        pairs = enumerate(d)
    elif isinstance(d, dict):
        pairs = d.items()
    result = []
    for k,v in pairs:
        if isinstance(v, basestring):
            try:
                # The %f format code is only supported in Python >= 2.6.
                # For Python <= 2.5 strip off microseconds
                # v = datetime.datetime.strptime(v.rsplit('.', 1)[0],
                #     '%Y-%m-%dT%H:%M:%S')
                v = datetime.datetime.strptime(v, '%Y-%m-%dT%H:%M:%S')
            except ValueError:
                try:
                    v = datetime.datetime.strptime(v, '%Y-%m-%d').date()
                except ValueError:
                    pass
        elif isinstance(v, (dict, list)):
            v = datetime_decoder(v)
        result.append((k, v))
    if isinstance(d, list):
        return [x[1] for x in result]
    elif isinstance(d, dict):
        return dict(result)
resultcache = dict()
global_id = 0
instance_attrs = ('__base_endpoint__', '__endpoint__', '__session__', '__interface__',
					'__rpccall__', '__rpccall_batch__', '__docall__', '__parse_response__',
					'__marshall_args__', '__rhash__', '__request_obj__',
					'__batch__', '__results__', '__id__', '__locals__', '__called__')
class Client(object):
	# allows using results of functions in batch calls
	def val(self, v):
		return self.__marshall_args__(*[v])[0]
	def __marshall_args__(self, *args, **kwargs):
		# not callable? regular value
		# callable? get index into batch values (by calling) to get request and make the call
		args = list(args)
		for arg in range(0, len(args)):
			if callable(args[arg]):
				if args[arg]() not in self.__called__:
					self.__called__.append(args[arg]())
					args[arg] = self.__docall__(args[arg]())
					self.__results__.append(args[arg])
				else:
					args[arg] = self.__results__[self.__called__.index(args[arg]())]
		for arg in kwargs:
			if callable(kwargs[arg]):
				if kwargs[arg]() not in self.__called__:
					self.__called__.append(kwargs[arg]())
					kwargs[arg] = self.__docall__(kwargs[arg]())
					self.__results__.append(kwargs[arg])
				else:
					kwargs[arg] = self.__results__[self.__called__.index(kwargs[arg]())]
		return args or kwargs
	def __parse_response__(self, val):
		if isinstance(val, basestring):
			return self.__parse_response__(simplejson.loads(val, object_hook=datetime_decoder))
		if isinstance(val, list):
			return [self.__parse_response__(x) for x in val]
		# error?
		if 'error' in val:
			raise RemoteException(val['error']['data']['exception'], val['error']['data']['message'])
		# remote object reference?
		if isinstance(val['result'], basestring) and 'hash:' in val['result']:
			return self.__class__(self.__base_endpoint__, val['result'].replace('hash:',''), self.__session__)
		# subclass with custom processing?
		if hasattr(self, '__resolve_references__'):
			return self.__resolve_references__(val['result'])
		# no special treatment
		return val['result']
	def __request_obj__(self, func, *args, **kwargs):
		global global_id
		params = self.__marshall_args__(*args, **kwargs)
		d = dict(jsonrpc="2.0",
				 id=global_id,
				 method=func,
				 params=params)
		global_id += 1
		return d
	def __rpccall__(self, endpoint, func, *args, **kwargs):
		if isinstance(func, APIClient):
			out = []
			for i in range(0, len(func.__batch__)):
				func.__batch__[i]['params'] = func.__marshall_args__(*func.__batch__[i]['params'])
			for f in func.__batch__:
				if f not in func.__called__:
					out.append(f)
			d = out
		else:
			d = self.__request_obj__(func, *args, **kwargs)
		if hasattr(self, '__batch__'):
			self.__batch__.append(d)
			i = len(self.__batch__) - 1
			return lambda: d if len(self.__results__) < i else self.__results__[i]
		heads = {'content-type': 'application/json'}
		return self.__parse_response__(self.__session__.post(endpoint, data=simplejson.dumps(d), headers=heads).text)
	def __docall__(self, req):
		heads = {'content-type': 'application/json'}
		return self.__parse_response__(self.__session__.post(self.__endpoint__, data=simplejson.dumps(req), headers=heads).text)
	# make it into a context handler!
	# with inst as batch:
	# 		a = inst.method()		# a() gets value
	#		b = inst.method2()		# b() gets value
	#		c = inst.method3(a)		# can use deferreds directly but they convert to non-batch calls
	def __enter__(self):
		c = copy(self)
		c.__batch__ = []
		c.__called__ = []
		c.__results__ = []
		self.__locals__ = local()
		self.__locals__.batch = c
		return c
	def __exit__(self, *args):
		c = self.__locals__.batch
		if not [x for x in args if x]:
			c.__results__ += self.__rpccall__(c.__endpoint__, c)
	def __init__(self, base_endpoint, this_endpoint='', session=None):
		self.__base_endpoint__ = base_endpoint
		self.__endpoint__ = '/'.join([base_endpoint, this_endpoint]).rstrip('/')
		self.__session__ = session or requests.session()
		self.__id__ = 0
		self.__interface__ = self.__rpccall__(self.__endpoint__, '__interface__')
		self.__rhash__ = 'hash:%i' % self.__interface__['hash']
		self.__locals__ = None
	def __getattr__(self, item):
		if item in instance_attrs:
			return object.__getattribute__(self, item)
		key = (self.__rhash__, item)
		if item not in self.__interface__['funcs'] and item not in self.__interface__['attrs']:
			raise AttributeError('no attribute named %s exists' % item)
		result = None
		if key not in resultcache:
			if item in self.__interface__['funcs']:
				resultcache[key] = functools.partial(self.__rpccall__, self.__endpoint__, item)
			if item in self.__interface__['attrs']:
				result = self.__rpccall__(self.__base_endpoint__, 'globals.getattr', self.__rhash__, item)
				# only store clients
				if isinstance(result, Client):
					resultcache[key] = result
		return resultcache.get(key, result)
	def __setattr__(self, item, value):
		if item in instance_attrs:
			return object.__setattr__(self, item, value)
		if item in self.__interface__['funcs']:
			raise ValueError('will not attempt to change functions')
		if item in self.__interface__['attrs']:
			self.__rpccall__(self.__base_endpoint__, 'globals.setattr', self, item, value)

	def __json__(self):
		return self.__rhash__

	def __dir__(self):
		return sorted(self.__interface__['funcs'] + self.__interface__['attrs'])

objcache = dict()

class SqlRef(dict):
	def __getitem__(self, item):
		i = dict.__getitem__(self, item)
		if isinstance(i, dict) and 'sqlref' in i:
			# return resolved values instead!
			key = (i['sqlref']['name'], tuple(i['sqlref']['items']))
			if key not in objcache:
				objcache[key] =	self.__resolver__.__rpccall__(self.__resolver__.__base_endpoint__,
																'api.database.get',
																i['sqlref']['name'],
																i['sqlref']['items'])
				if len(objcache[key]) == 1:
					objcache[key] = objcache[key][0]
			return objcache[key]
		return i
	def __getattr__(self, item):
		if item in self:
			return object.__getattribute__(self, '__getitem__')(item)
		return object.__getattribute__(self, item)
	def __raw__(self):
		return {x:dict.__getitem__(self, x) for x in self}

	def __str__(self):
		return dict.__str__(self.__raw__())

	def __repr__(self):
		return dict.__repr__(self.__raw__())

	def __dir__(self):
		return self.__raw__().keys()



class APIClient(Client):
	def __marshall_args__(self, *args, **kwargs):
		ret = super(APIClient, self).__marshall_args__(*args, **kwargs)
		for i in range(0, len(ret) - 1):
			if isinstance(ret[i], Client):
				ret[i] = json(ret[i])
			elif isinstance(ret[i], dict) and '__meta__' in ret[i]:
				ret[i] = {'one': True,
						  'sqlref': {'name': ret[i]['__meta__']['name'],
									 'items': ret[i]['__meta__']['id']}}
		return ret
	def __resolve_references__(self, ret):
		def parse_sqlref(obj):
			if isinstance(obj, dict):
				for k in obj:
					if isinstance(obj[k], dict) and 'sqlref' in obj[k]:
						s = SqlRef(obj)
						s.__resolver__ = self
						return s
			elif isinstance(obj, list):
				return [parse_sqlref(x) for x in obj]
			return obj
		return parse_sqlref(ret)

def build_routes(api, sess=lambda:None):
	api_obj = API(api)
	@bottle.get('/api')
	def api():
		return api_obj.__jsoncall__(sess)
	@bottle.get('/api/:objid')
	def api(objid):
		global objects
		return objects[int(objid)].__jsoncall__(sess)
	@bottle.post('/api')
	def api():
		return api_obj.__jsoncall__(sess)
	@bottle.post('/api/:objid')
	def api(objid):
		global objects
		return objects[int(objid)].__jsoncall__(sess)

if __name__ == '__main__':
	build_routes(APIObj())
	@route('/')
	def main():
		return SimpleTemplate(source='<html><head> <script src="//ajax.googleapis.com/ajax/libs/prototype/1.7.1.0/prototype.js"></script><script src="/js/api.js" type="text/javascript"></script></head></html>').render()
	@route('/js/api.js')
	def js():
		return bottle.static_file('api.js', root='js')

	bottle.run(bottle.default_app(), port=9055)