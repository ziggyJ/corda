Hello, World! Pt.4 - Node interaction
=====================================

.. contents::

So far, we have only interacted with our node via the interactive shell. However, we'll usually want to interact with
the node using a client or webserver. How can we write these applications that connect to our node?

Node RPC connections
--------------------
When starting nodes via DemoBench, you may have noticed that each node exposed an RPC connection address. This is the
address that the node's owner is expected to connect to her node on, by using the `CordaRPCClient`_ library. This
library allows you to easily write clients in a JVM-compatible language to interact with a running node. The library
connects to the node using a message queue protocol and then provides a simple RPC interface to interact with the node.
You make calls on a JVM object as normal, and the marshalling back and forth is handled for you.

Using CordaRPCClient
--------------------
TODO - Turn this into instructions for writing their own RPC client.

`CordaRPCClient`_ provides a ``start`` method that takes the node's RPC address and returns a `CordaRPCConnection`_.
`CordaRPCConnection`_ provides a ``proxy`` method that takes an RPC username and password and returns a `CordaRPCOps`_
object that you can use to interact with the node.

Here is an example of using `CordaRPCClient`_ to connect to a node and log the current time on its internal clock:

.. container:: codeset

   .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcExample.kt
      :language: kotlin
      :start-after: START 1
      :end-before: END 1

   .. literalinclude:: example-code/src/main/java/net/corda/docs/ClientRpcExampleJava.java
      :language: java
      :start-after: START 1
      :end-before: END 1

Extending our RPC client
------------------------
TODO

Conclusion
----------
We have seen a new way of configuring and running a network of nodes.

Next steps
----------
You should now be ready to develop your own CorDapps. You can find a list of sample CorDapps
`here <https://www.corda.net/samples/>`_. As you write CorDapps, you'll also want to learn more about the
:doc:`Corda API <corda-api>`.

If you get stuck at any point, please reach out on `Slack <https://slack.corda.net/>`_ or
`Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_.