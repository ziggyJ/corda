# Accounts design

## Requirements for accounts

Matthew Nesbit noted the following on slack which I thought was an excellent overview of the reasons why we need to support accounts on Corda nodes;

> In my mind ‘I need accounts’ might mean any combination of:

> 1. I need accounts, because every customer must hold their own keys and be able to portably spend/authorise individual states in the future, possibly even using a different provider.
? 2. I need accounts so that my flows and vault queries on behalf of customer facing operations only show data relevant to that user (I may well have supervisory users seeing more).
> 3. I need accounts so that I can correlate activities with external systems and assure that the transactions are happening.
> 4. I need accounts so that I can check all sorts of node local metadata that affects whether I authorise those customer actions, or price the actions differently, or allow this flow to complete.
> 5. I need accounts as the data has to be separately encrypted for compliance and isolation reasons. Perhaps the data isn’t mine and whilst I operate the node I don’t own the data.
> 6. I need accounts as the individual users have individual balances, but the node operating organisation is responsible for netting these flows and providing supporting liquidity i.e. fractional reserve banking.

For this document we are just concentrating on points 2, 4 and 6: where the node owner will be maintaining balances of money, assets or agreements on behalf of its own customers.

### Requirements in more detail

Accounting for tokens:

* The node should be able to partition pools of various types tokens into accounts.
* Account holders should not be able to spend more than they have in their account
* Not all tokens necessarily need to be allocated to accounts - some can be “unallocated”
* The node, outside the context of an account, should not be able to spend more than the unallocated tokens. Doing so would leave the account holders with a deficit.
* Account balances need to be updated atomically with token receipts and spends
* The node should be able to specify which account should be updated when recording transactions
* The sum of all account amounts should equal the amount of tokens in the pool at all times
* The fungible states which are to be accounted for should not contain any account information
* The node should be able to enumerate all account balances

Accounting for non-tokens:

* The node should be able to tag states to an account
* The node should be able to enumerate all StateRefs tagged for all accounts
* Account holders should only be able to query their own stuff

### Out of scope

* User key management and signing
* Account reference data over and above account ID

## Design:

* This doesn’t actually need the “expose JPA to flows” feature - it’s possible now using the jdbcConnection property of ServiceHub.
* Create an accounts table which keeps track of aggregate amounts for a list of accounts and token types.
* Create an accounts table which keeps track of specific state refs belonging to a particular account. This is required to keep track of non-tokens, e.g. non-fungible states.
* Wrap both these tables with a CordaService - the accounts service. Expose methods on this service to add/remove/credit/debit/etc.
* Add some flows which allow the node to add accounts, list accounts, remove accounts, view account balances, select coins from accounts, etc.
* When a state is persisted, an account ID must be specified. If the accountID is unknown then the state is still persisted but allocated to a “suspense” account. Potentially these states can be returned to sender but typically a “does account exist” check will be performed before transactions are committed to the ledger.
* Note: can make inter-account transfers without requiring a ledger transaction.

Rationale:

* Unless customers control their own keys, there is no need to "tag" each fungible state with an account ID. The same result can be achieved by storing and updating account balances.
* This cannot work for non-fungible tokens and non-financial data. Here, individual states must be tagged with an account ID.

## Outstanding questions

**How do we prevent RPC users from querying all data on the node?**

* We somehow need to segregate the vault by account.

**Should there be a one to one mapping between accounts and RPC users?**

* This is up for discussion.

**Can there be tokens which are “unaccounted for”. Does this just complicate things?**

**This feature is implemented as a CorDapp. How does the CorDapp interact with native node functionality such as coin selection and transaction recording?**

* These operations can circumvent the accounts CorDapp

