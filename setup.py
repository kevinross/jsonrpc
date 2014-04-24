from setuptools import setup

setup(name='jsonrpc',
      version='1.0',
      description='implementation of JSON-RPC v2 client and server',
      url='http://github.com/r0ssar00/jsonrpc',
      author='Kevin Ross',
      author_email='r0ssar00@gmail.com',
      license='GPLv2',
      package_dir={'jsonrpc':'src/main/python'},
      packages=['jsonrpc'],
      install_requires=['bottle',
                        'requests',
                        'simplejson'],
      zip_safe=True)

