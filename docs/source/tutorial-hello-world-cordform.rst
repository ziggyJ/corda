Hello, World! Pt.5 - Cordform
=============================

.. contents::

Until now, we've been using the DemoBench tool to create Corda networks. An alternative, which we're going to explore
in this tutorial, is a Gradle plugin called Cordform.

Our Cordform task
-----------------
If you open the root ``build.gradle`` file in our CorDapp project, you'll see a Cordform task called ``deployNodes``
that defines and configures the three nodes that we're going to deploy:

* ``Notary``
* ``PartyA``
* ``PartyB``

.. code:: bash

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        node {
            name "O=Notary,L=London,C=GB"
            notary = [validating : false]
            p2pPort 10002
            rpcSettings {
                address("localhost:10003")
                adminAddress("localhost:10043")
            }
            cordapps = [
                    "$project.group:cordapp-contracts-states:$project.version",
                    "$project.group:cordapp:$project.version",
                    "$corda_release_group:corda-finance:$corda_release_version"
            ]
        }
        node {
            name "O=PartyA,L=London,C=GB"
            p2pPort 10005
            rpcSettings {
                address("localhost:10006")
                adminAddress("localhost:10046")
            }
            webPort 10007
            cordapps = [
                    "$project.group:cordapp-contracts-states:$project.version",
                    "$project.group:cordapp:$project.version",
                    "$corda_release_group:corda-finance:$corda_release_version"
            ]
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
        }
        node {
            name "O=PartyB,L=New York,C=US"
            p2pPort 10008
            rpcSettings {
                address("localhost:10009")
                adminAddress("localhost:10049")
            }
            webPort 10010
            cordapps = [
                    "$project.group:cordapp-contracts-states:$project.version",
                    "$project.group:cordapp:$project.version",
                    "$corda_release_group:corda-finance:$corda_release_version"
            ]
            rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
        }
    }

Creating the nodes
------------------
Running the ``deployNodes`` task will create our network by:

* Generate a CorDapp JAR for each one of the project's modules
* For each node definition:

    * Generate a node
    * Configure the node
    * Install the required CorDapps

* "Bootstrap" the network
    * Bootstrapping allows a set of devmode nodes to speak to one another without requiring a network map server

Let's create the network now by running the following command from the root of the project:

.. code:: bash

    // On Windows
    gradlew clean deployNodes

    // On Mac
    ./gradlew clean deployNodes

Once ``deployNodes`` has finished running, you will find three node folders under ``build/nodes``. Each node folder has
the following structure:

    .. code:: bash

        .
        ├── additional-node-infos
        ├── certificates
        ├── corda-webserver.jar
        ├── corda.jar
        ├── cordapps
        │   ├── corda-finance-3.2-corda.jar
        │   ├── cordapp-0.1.jar
        │   ├── cordapp-contracts-states-0.1.jar
        │   └── cordapp-template-kotlin-0.1.jar
        ├── drivers
        ├── logs
        ├── network-parameters
        ├── node.conf
        ├── nodeInfo-E4477B559304AADFC0638772C0956A38FA2E2A7A5EB0E65D0D83E5884831879A
        └── persistence.mv.db

Running a single node
---------------------
To start a Corda node, we simply start the Corda JAR, which will automatically load the required CorDapps. Let's start
the ``PartyA`` node now. Open a terminal in the ``PartyA`` folder and run:

.. code:: bash

    java -jar corda.jar

The node will start and pause displaying its terminal. Let's close that terminal by entering ``bye``.

Running all the nodes together
------------------------------
Cordform also creates a script called ``runnodes`` that steps through and starts all our nodes one-by-one. Open a
terminal in the root of the project and run:

.. code:: bash

    // On Windows
    build/nodes/runnodes.bat

    // On Mac
    build/nodes/runnodes

This script will start five terminal windows in all:

* A terminal window for each node
* An additional terminal window for the ``PartyA`` and ``PartyB`` development webservers

Give each node a moment to start. You'll know the node is ready when its terminal windows displays the message,
"Welcome to the Corda interactive shell.".

  .. image:: resources/running_node.png
     :scale: 25%
     :align: center

We can now interact with the nodes by using the :doc:`same instructions as before <hello-world-running>`.

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