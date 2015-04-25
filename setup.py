from setuptools import setup

setup(name='jsonrpc',
      version='1.0',
      description='implementation of JSON-RPC v2 client and server',
      url='https://github.com/kevinross/jsonrpc',
      author='Kevin Ross',
      author_email='me@kevinross.name',
      license='GPLv2',
      package_dir={'jsonrpc':'src/main/python'},
      packages=['jsonrpc'],
      install_requires=['bottle',
                        'requests',
                        'simplejson'],
      zip_safe=True)

