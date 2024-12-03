package payment.demo.perfTests;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.http.HttpDsl.*;
import static io.gatling.recorder.internal.bouncycastle.oer.its.ieee1609dot2.basetypes.Duration.seconds;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TransactionProcessingSimulation extends Simulation {

    Config config = ConfigFactory.load();

    List<Map<String, Object>> randomAccounts = RandomDataGenerator
        .generateRandomAccounts(
            config.getInt("perf-test.sim.accounts.count"),
            config.getInt("perf-test.sim.accounts.min-balance"),
            config.getInt("perf-test.sim.accounts.max-balance")
        );

    List<Map<String, Object>> txWithSequentialFundsFlow = RandomDataGenerator
        .generateRandomTransactions(
            randomAccounts,
            config.getInt("perf-test.sim.transactions.count"),
            config.getInt("perf-test.sim.transactions.amount")
        );

    Iterator<Map<String, Object>> accountFeeder = randomAccounts
        .stream()
        .iterator();

    Iterator<Map<String, Object>> transactionFeeder = txWithSequentialFundsFlow
        .stream()
        .iterator();

    ChainBuilder loadAccount = exec(
        feed(accountFeeder),
        http("Load Account")
            .post("/account/#{account}/create/#{balance}")
            .asJson()
            .check(status().is(200))
    );

    ChainBuilder processTransaction = exec(
        feed(transactionFeeder),
        http("Process Transaction")
            .post("/transaction/#{id}/process")
            .body(StringBody("{\"type\":\"request\",\"from\":\"#{from}\",\"to\":\"#{to}\",\"processId\":\"#{processId}\",\"amount\":#{amount}}"))
            .asJson()
            .check(status().is(200))
    );

    HttpProtocolBuilder httpProtocol =
        http.baseUrl(config.getString("perf-test.baseUrl"))
            .acceptHeader("application/json");

    ScenarioBuilder accounts = scenario("Load Accounts Scenario").exec(loadAccount);

    ScenarioBuilder transactions = scenario("Process Transactions Scenario").exec(processTransaction);

    {
        setUp(

            //Load the accounts first
            accounts.injectOpen(atOnceUsers(config.getInt("perf-test.sim.accounts.count"))),

            //After accounts loaded, run the transactions using the accounts loaded
            transactions.injectOpen(

                //Let accounts finish loading...
                nothingFor(Duration.ofSeconds(config.getInt("perf-test.sim.accounts.load-pause"))),

                //Submit #of transactions to process ramped over x number of seconds...
                rampUsers(10).during(Duration.ofSeconds(2))

                //rampUsers(500).during(Duration.ofSeconds(5))//,

                //atOnceUsers(config.getInt("perf-test.sim.transactions.count"))

            )

        ).protocols(httpProtocol);
    }

}
