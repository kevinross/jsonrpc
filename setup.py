from setuptools import setup

setup(name='jsonrpcrmi',
      version='1.0',
      description='implementation of JSON-RPC v2 client and server inspired by Java\'s RMI',
      url='https://github.com/kevinross/jsonrpc',
      author='Kevin Ross',
      author_email='me@kevinross.name',
      license='GPLv2',
      package_dir={'jsonrpcrmi':'src/main/python/jsonrpc'},
      packages=['jsonrpcrmi'],
      install_requires=['bottle',
                        'requests',
                        'simplejson'],
      zip_safe=True)

