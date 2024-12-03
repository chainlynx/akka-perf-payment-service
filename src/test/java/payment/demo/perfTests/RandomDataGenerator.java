package payment.demo.perfTests;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RandomDataGenerator {

    //private static final int SEQUENCE_LENGTH = 9;
    //private static final String FORMAT_PATTERN = "%0" + SEQUENCE_LENGTH + "d";
    private static final String SECRET_KEY = "secret";

    public static List<Map<String, Object>> generateRandomAccounts(Integer numberOfAccounts, Integer minBalance, Integer maxBalance){
        List<Map<String, Object>> accountData = new ArrayList<>();
        for(int i = 0; i < numberOfAccounts; i++){
            Map<String, Object> account = new HashMap<>();
            account.put("account", randomId());
            account.put("balance", randomInt(minBalance, maxBalance));
            accountData.add(account);
        }
        return accountData;
    }

    // Sequential Movement of Funds A -> B. B -> C, C -> A
    public static List<Map<String, Object>> generateRandomTransactions(List<Map<String, Object>> accounts, Integer numberOfTransactions, Integer txBalance){
        List<Map<String, Object>> transactionData = new ArrayList<>();
        var accountSize = accounts.size();
        var idx = 0;
        var processId = randomId();
        for(int i = 0; i < numberOfTransactions; i++){
            String fromAccount = (String) accounts.get(idx++).get("account");
            if(idx > accountSize - 1) idx = 0;
            String toAccount = (String) accounts.get(idx).get("account");
            //String seq = String.format(FORMAT_PATTERN, i+1);
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("from", fromAccount);
            transaction.put("to", toAccount);
            transaction.put("amount", txBalance);
            transaction.put("processId", processId); //intentionally the same for each transaction to provide a grouping ID
            transaction.put("id", HashUtils.encode(SECRET_KEY, fromAccount, toAccount, txBalance, processId));
            transactionData.add(transaction);
        }
        return transactionData;
    }

    private static int randomInt(int min, int max){
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static String randomId(){
        return UUID.randomUUID().toString().substring(0, 8);
    }

}