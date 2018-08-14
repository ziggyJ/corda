.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Running our CorDapp
===================

Now that we've written a CorDapp, it's time to test it by running it on a network of Corda nodes.

Compiling our CorDapp
---------------------
Open a terminal window at the root of the template folder and run the following command:

* Unix/Mac OSX: ``./gradlew jar``
* Windows: ``gradlew.bat jar``

This will compile each module in the project into its own CorDapp JAR and output it to the module's ``build/libs``
folder. In our case, we are interested in two CorDapps:

* ``cordapp-contracts-states/build/libs/cordapp-contracts-states-0.1.jar``
* ``cordapp/build/libs/cordapp-0.1.jar``

The first contains our state and contract definitions. The second contains our flow definitions.

Installing our CorDapp
----------------------
We are going to run our CorDapp on the same network we defined last time (Notary, Bank of Breakfast Tea, and Bank of
Big Apples). Perform the following steps:

* Start DemoBench
* Create a non-validating notary as before
* When adding the Bank of Breakfast Tea and Bank of Big Apples nodes:
    * Click ``Add CorDapp``
    * Navigate to the ``build/libs`` folder
    * Select our CorDapp JARs - ``cordapp-contracts-states-0.1.jar`` and ``cordapp-0.1.jar``
    * Click ``Start Node``

Interacting with the nodes
--------------------------
Once our nodes are running, let's order Bank of Breakfast Tea to create an IOU of 99 with Bank of Big Apples. We do
this by going to Bank of Breakfast Tea tab and starting the ``IOUFlow`` by typing:

.. container:: codeset

    .. code-block:: kotlin

        start IOUFlow iouValue: 99, otherParty: "Bank of Big Apples"

This single command will cause Bank of Breakfast Tea and Bank of Big Apples to automatically agree an IOU. This is one
of the great strengths of the flow framework - it allows you to reduce complex negotiation and update processes into a
single function call.

If the flow worked, it should have recorded a new IOU in the vaults of both Bank of Breakfast Tea and Bank of Big
Apples. As before, we can check the contents of each node's vault by running:

.. code-block:: bash

        run vaultQuery contractStateType: com.template.IOUState

The vaults of Bank of Breakfast Tea and Bank of Big Apples should both display the following output:

.. code:: bash

    states:
    - state:
        data:
          value: 99
          lender: "O=Bank of Breakfast Tea,L=Liverpool,C=GB"
          borrower: "O=Bank of Big Apples,L=New York,C=US"
          participants:
          - "O=Bank of Breakfast Tea,L=Liverpool,C=GB"
          - "O=Bank of Big Apples,L=New York,C=US"
        contract: "com.template.contract.IOUContract"
        notary: "O=Notary,L=Rome,C=IT"
        encumbrance: null
        constraint:
          attachmentId: "F578320232CAB87BB1E919F3E5DB9D81B7346F9D7EA6D9155DC0F7BA8E472552"
      ref:
        txhash: "5CED068E790A347B0DD1C6BB5B2B463406807F95E080037208627565E6A2103B"
        index: 0
    statesMetadata:
    - ref:
        txhash: "5CED068E790A347B0DD1C6BB5B2B463406807F95E080037208627565E6A2103B"
        index: 0
      contractStateClassName: "com.template.state.IOUState"
      recordedTime: 1506415268.875000000
      consumedTime: null
      status: "UNCONSUMED"
      notary: "O=Notary,L=Rome,C=IT"
      lockId: null
      lockUpdateTime: 1506415269.548000000
    totalStatesAvailable: -1
    stateTypes: "UNCONSUMED"
    otherResults: []

This is the transaction issuing our ``IOUState`` onto a ledger.

Conclusion
----------
We have written a simple CorDapp that allows IOUs to be issued onto the ledger. Our CorDapp is made up of two key
parts:

* The ``IOUState``, representing IOUs on the ledger
* The ``IOUFlow``, orchestrating the process of agreeing the creation of an IOU on-ledger

After completing this tutorial, your CorDapp should look like this:

* Java: https://github.com/corda/corda-tut1-solution-java
* Kotlin: https://github.com/corda/corda-tut1-solution-kotlin

Next steps
----------
There are a number of improvements we could make to this CorDapp:

* We could add unit tests, using the contract-test and flow-test frameworks
* We could change ``IOUState.value`` from an integer to a proper amount of a given currency
* We could add an API, to make it easier to interact with the CorDapp

But for now, the biggest priority is to add an ``IOUContract`` imposing constraints on the evolution of each
``IOUState`` over time. This will be the focus of our next tutorial.
