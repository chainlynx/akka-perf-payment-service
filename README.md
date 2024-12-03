# java-perf-payment-service
Gatling performance test demo to showcase Akka 3 workflows and distributed transaction processing for project: akka-sdk-payment-service

## Prerequisites
- Java 21 (we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/))
- [Apache Maven](https://maven.apache.org/install.html)

## Overview of Perf Test Code
- There are a few building blocks that are used to create the performance test:

### Generate random testing data
- Generate random accounts and amounts to be used in the test.
- Note: The test will iterate through the available accounts, in a round-robin fashion. 
- Example: From Account A -> B, B -> C, C -> A. This ensures we can use a relatively small number of accounts to simulate a large number of transactions.
```shell
    List<Map<String, Object>> randomAccounts = RandomDataGenerator
        .generateRandomAccounts(
            config.getInt("perf-test.sim.accounts.count"),
            config.getInt("perf-test.sim.accounts.min-balance"),
            config.getInt("perf-test.sim.accounts.max-balance")
        );
```
- Generate random transactions to be used in the test.
```shell
    List<Map<String, Object>> randomTransactions = RandomDataGenerator
        .generateRandomTransactions(
            config.getInt("perf-test.sim.transactions.count"),
            config.getInt("perf-test.sim.transactions.min-amount"),
            config.getInt("perf-test.sim.transactions.max-amount")
        );
```

### Create testing data Iterators
- For the performance test, we need to create iterators for the random accounts and transactions, which will be fed into the ChainBuilder steps to feed data per simulated user request.
```shell
    Iterator<Map<String, Object>> accountFeeder = randomAccounts
        .stream()
        .iterator();

    Iterator<Map<String, Object>> transactionFeeder = txWithSequentialFundsFlow
        .stream()
        .iterator();
```

### Create account loading simulation
- This pulls an entry from the accountFeeder and post's it to the account creation endpoint.
```shell
    ChainBuilder loadAccount = exec(
        feed(accountFeeder),
        http("Load Account")
            .post("/account/#{account}/create/#{balance}")
            .asJson()
            .check(status().is(200))
    );
```

### Create transaction simulation
- This pulls an entry from the transactionFeeder and post's it to the transaction endpoint.
- Note: For proper serialization, a type parameter of 'request' has been set.
```shell
    ChainBuilder processTransaction = exec(
        feed(transactionFeeder),
        http("Process Transaction")
            .post("/transaction/#{id}/process")
            .body(StringBody("{\"type\":\"request\",\"from\":\"#{from}\",\"to\":\"#{to}\",\"processId\":\"#{processId}\",\"amount\":#{amount}}"))
            .asJson()
            .check(status().is(200))
    );
```

### Setup protocol
- This sets up the baseUrl and the headers for the performance test/target.
```shell
    HttpProtocolBuilder httpProtocol =
        http.baseUrl(config.getString("perf-test.baseUrl"))
            .acceptHeader("application/json");
```

### Setup scenarios (Gatling scenarios)
- This sets up the scenarios for the performance test.
- Basically for each user in the follow setup block, the account loading and transaction processing steps are executed.
```shell
    ScenarioBuilder accounts = scenario("Load Accounts Scenario").exec(loadAccount);

    ScenarioBuilder transactions = scenario("Process Transactions Scenario").exec(processTransaction);
```

### Setup simulation
- Note: In the rampUsers call, I've hard-coded values. This was just for simplicity in trying different numbers.
- It's important the the sum of all the rampUsers calls is less than or equal to the number of transactions specified in generating the dummy data. Otherwise you may get an index out of bounds exception.
```shell
{
        setUp(

            //Load the accounts first
            accounts.injectOpen(atOnceUsers(config.getInt("perf-test.sim.accounts.count"))),

            //After accounts loaded, run the transactions using the accounts loaded
            transactions.injectOpen(
                
                //Let accounts finish loading...
                nothingFor(Duration.ofSeconds(config.getInt("perf-test.sim.accounts.load-pause"))),
                
                //Submit #of transactions to process ramped over x number of seconds...
                rampUsers(100).during(Duration.ofSeconds(5)),

                rampUsers(500).during(Duration.ofSeconds(5))//,

                //atOnceUsers(config.getInt("perf-test.sim.transactions.count"))

            )

        ).protocols(httpProtocol);
    }
```

## Running the performance tests
- With the proper target baseUrl set in configuration, number of accounts, and simulated transactions specified, you can run the performance test with the following maven command from the root of the perf test project.
```shell
mvn gatling:test
```
