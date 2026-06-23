Quickstart
==========

Installation
-----------

.. code-block:: bash

   pip install cloudpool-sdk

   # With async support:
   pip install cloudpool-sdk[async]

   # Full installation (async + dotenv + keyring):
   pip install cloudpool-sdk[all]

Basic Usage
-----------

Sync client:

.. code-block:: python

   from cloudpool import CloudPool

   cp = CloudPool(api_key="sk-...")
   me = cp.auth.login("user@example.com", "password")
   files = cp.files.list()
   cp.close()

Async client:

.. code-block:: python

   from cloudpool import CloudPool

   async with CloudPool.async_client(api_key="sk-...") as cp:
       me = await cp.auth.me()
       files = await cp.files.list()

Configuration
-------------

The SDK follows the 12-factor app principles:

1. Constructor arguments (highest priority)
2. Environment variables (``CLOUDPOOL_*``)
3. ``.env`` file in the current directory
4. Config file (``~/.cloudpool/config.yaml``)
5. Built-in defaults
