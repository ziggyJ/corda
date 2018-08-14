Hello, World!
=============

.. contents::

Corda is an open-source blockchain platform that allows you to agree updates to a shared ledger with your
counterparties. In this, the first of a sequence of Hello, World! tutorials, we'll create a local Corda network and
perform our first ledger updates.

DemoBench
---------
We'll create our Corda network using a tool called DemoBench, a standalone desktop application that makes it easy to
configure and launch local Corda nodes. Download and run the installer for the latest Corda release from the
`Corda website <https://www.corda.net/downloads>`_.

Creating our network
--------------------
Corda network are made up of nodes. Our network will have three nodes:

* A notary node who will prevent double-spends. You can read more about notaries in
  :doc:`key-concepts-notaries`
* ``Bank of Breakfast Tea``
* ``Bank of Big Apples``

Open DemoBench and you will be presented with the new node tab. The first node you create using DemoBench will always
be the network's notary node (production Corda networks generally have multiple notaries). Leave the ``Legal name`` and
``Nearest city`` fields untouched and click ``Start node`` to create the notary node.

Click ``Add node`` then ``Start node`` twice more to add the ``Bank of Breakfast Tea`` and ``Bank of Big Apples``
nodes to our Corda network. The nodes names and nearest cities are preconfigured by DemoBench.

Interacting with our nodes
--------------------------

The node shell
~~~~~~~~~~~~~~
We'll start by looking at the Bank of Breakfast Tea node. On the ``Bank of Breakfast Tea`` tab, we have access to Bank
of Breakfast Tea's interactive shell. This is a console that's enabled in development mode to allow a node operator to
interact with their node without creating their own client to speak to the node via remote procedure calls (RPC).

We can see that Bank of Breakfast Tea has two CorDapps loaded:

* ``corda-finance``
* ``corda-core``

CorDapps (Corda Distributed Applications) are distributed applications that run on the Corda platform. We'll define our
own CorDapp in the next tutorial.

By typing ``help`` in the shell, we can see a list of commands available to the node operator. By typing ``run``, we
can see a list of the RPC operations the node operator can ask their node to execute.

Let's try one of these. Enter ``run nodeInfo`` into the shell to print out Bank of Breakfast Tea's identity:

.. code:: bash

        {
            "legalIdentities":[{"name":"O=Bank of Breakfast Tea, L=Liverpool, C=GB"}],
            "addresses":["localhost:10005"],
            "serial":XXX,
            "platformVersion":X
        }

In Corda, each node on the network has a well-known identity tied to a certificate. This allows nodes to link
signatures to real-world legal identities.

Flows
~~~~~
In Corda, the ledger is updated by calling methods registered on the node called *flows*. Enter ``flow list`` into
the shell to see Bank of Breakfast Tea's registered flows:

.. code:: bash

    net.corda.core.flows.ContractUpgradeFlow$Authorise
    net.corda.core.flows.ContractUpgradeFlow$Deauthorise
    net.corda.core.flows.ContractUpgradeFlow$Initiate
    net.corda.finance.flows.CashConfigDataFlow
    net.corda.finance.flows.CashExitFlow
    net.corda.finance.flows.CashIssueAndPaymentFlow
    net.corda.finance.flows.CashIssueFlow
    net.corda.finance.flows.CashPaymentFlow

The result is a combination of the contract-upgrade flows defined in ``corda-core``, and the cash-related flows
defined in ``corda-finance``.

Let's run one of these flows. Enter the following command into the shell to make Bank of Breakfast Tea run the
``CashIssueFlow`` to issue itself $100 of cash:

.. code:: bash

    flow start CashIssueFlow amount: $100, issuerBankPartyRef: HelloWorldCash, notary: Notary

A series of progress steps will be printed to the screen. Once we hit ``Done``, we know the flow was a success:

.. code:: bash

    ✓ Generating anonymous identities
    ✓ Generating transaction
    ✓ Signing transaction
    ✓ Finalising transaction
        ✓ Requesting signature by notary service
            ✓ Requesting signature by Notary service
            ✓ Validating response from Notary service
        ✓ Broadcasting transaction to participants
    ✓ Done

States
~~~~~~
In Corda, facts such as a node's ownership of an amount of cash are represented as *states*. These states are stored in
the node's *vault*. Enter the following command into the shell to see the cash currently held in Bank of Breakfast
Tea's vault:

.. code:: bash

    run vaultQuery contractStateType: net.corda.finance.contracts.asset.Cash$State

We see the $100 that Bank of Breakfast Tea just issued to itself:

.. code:: bash

    {
      "states" : [ {
        "state" : {
          "data" : {
            "amount" : "100.00 USD issued by O=Bank of Breakfast Tea, L=Liverpool, C=GB[48656C6C6F576F726C6443617368]",
            "owner" : "O=Bank of Breakfast Tea, L=Liverpool, C=GB",
            "exitKeys" : [ "MCowBQYDK2VwAyEAI+7y9RNReD7R29DGWvoYnmRAe08Zx1p05I4+si24moE=" ],
            "participants" : [ "O=Bank of Breakfast Tea, L=Liverpool, C=GB" ]
          },
          "contract" : "net.corda.finance.contracts.asset.Cash",
          "notary" : "O=Notary, L=Rome, C=IT",
          "encumbrance" : null,
          "constraint" : {
            "attachmentId" : "3ECECAA4C7E21559050BCDAD807ACFE6FCCB0F174036B18478942D0D5EB029E4"
          }
        },
        "ref" : {
          "txhash" : "F1577F164FA0819E47ACBF016F716983F0C11C900BBB472915FE24C4E62039F0",
          "index" : 0
        }
      } ],
      "statesMetadata" : [ {
        "ref" : {
          "txhash" : "F1577F164FA0819E47ACBF016F716983F0C11C900BBB472915FE24C4E62039F0",
          "index" : 0
        },
        "contractStateClassName" : "net.corda.finance.contracts.asset.Cash$State",
        "recordedTime" : 1534262348.620000000,
       "consumedTime" : null,
        "status" : "UNCONSUMED",
        "notary" : "O=Notary, L=Rome, C=IT",
        "lockId" : null,
        "lockUpdateTime" : 1534262348.754000000
      } ],
      "totalStatesAvailable" : -1,
      "stateTypes" : "UNCONSUMED",
      "otherResults" : [ ]
    }

In Corda, each fact is only known to the parties involved in the transaction. In the case of the cash issuance we just
performed, that's only Bank of Breakfast Tea. Switching to the ``Bank of Big Apples`` tab, enter the following command
into the shell to see the cash states that Bank of Big Apples's vault holds in **its** vault:

.. code:: bash

    run vaultQuery contractStateType: net.corda.finance.contracts.asset.Cash$State

What cash states can it see? None at all:

.. code:: bash

    {
      "states" : [ ],
      "statesMetadata" : [ ],
      "totalStatesAvailable" : -1,
      "stateTypes" : "UNCONSUMED",
      "otherResults" : [ ]
    }

Note that these states aren't just encrypted, for example. They have simply not been sent to Bank of Big Apples because
the transaction was not relevant to it.

Finality
~~~~~~~~
Now that Bank of Breakfast Tea has some cash, they can share it around the network.  Switching back to the
``Bank of Breakfast Tea`` tab, enter the following command into the shell to make Bank of Breakfast Tea run the
``CashPaymentFlow`` to transfer $50 of its cash to Bank of Big Apples:

.. code:: bash

    flow start CashPaymentFlow amount: $50, recipient: "Bank of Big Apples", anonymous: false

Again, a series of progress steps will be printed to the screen, culminating in ``Done``.

Let's check that Bank of Big Apples has received the cash. Switching to the ``Bank of Big Apples`` tab, enter the
following command into the shell to see the cash held in Bank of Big Apples's vault:

.. code:: bash

    run vaultQuery contractStateType: net.corda.finance.contracts.asset.Cash$State

You will see the $50 in Bank of Big Apples' vault. Note that the transaction is not awaiting block confirmations -
Corda has no such concept. Transaction finality is instant and irreversible.

Conclusion
----------
We have spun-up a local Corda network and used to ``corda-finance`` CorDapp to show some of the key characteristics of
the Corda platform that differentiate it from other blockchains:

* Corda networks are made up of nodes, each with a well-known identity
* The ledger is updated using flows
* Facts on the ledger are known as states and are only distributed on a need-to-know basis
* Transaction finality is instant

Next steps
----------
In running the ``corda-finance`` CorDapp, we're only scratching the surface of what Corda can do. In the next tutorial,
we'll define our own CorDapp to allow the issuance of IOUs on the ledger.