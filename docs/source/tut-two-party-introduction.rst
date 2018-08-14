Hello, World! Pt.3 - Contract constraints
=========================================

.. note:: This tutorial follows on from :doc:`Hello, World Pt.2 <hello-world-introduction>`.

In the Hello, World tutorial, we built a CorDapp allowing us to model IOUs on ledger. Our CorDapp was made up of two
elements:

* An ``IOUState``, representing IOUs on the ledger
* An ``IOUFlow``, orchestrating the process of agreeing the creation of an IOU on-ledger

However, our CorDapp did not impose any constraints on the evolution of IOUs on the ledger over time. Anyone was free
to create IOUs of any value, between any party.

In this tutorial, we'll write a contract to imposes rules on how an ``IOUState`` can change over time. In turn, this
will require some small changes to the flow we defined in the previous tutorial.

This tutorial is split into two sections:

.. toctree::
   :maxdepth: 1

   tut-two-party-contract
   tut-two-party-flow

We'll start by writing the contract.