package gatling.simulations;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Shared building blocks for MaxSearchSimulation and ConfirmationSimulation - both run the
 * exact same "one client" chain (login, then internal transfer), just with different
 * injection profiles. Kept here once instead of duplicated per spec's correlation example:
 *
 *   login (initiate -> confirm, save token)
 *   -> transfer initiate (save transactionId)
 *   -> transfer confirm (using debugCode read from the initiate response)
 *   -> transfer execute
 *
 * Each virtual user is one row from the feeder (phone/accountId/accountNumber/toAccountNumber)
 * - see load-testing/sql/seed_load_test_data.sql for how that CSV is produced. No token or
 * account is ever hardcoded or shared across virtual users.
 */
final class MiniBankChains {

    private MiniBankChains() {
    }

    static HttpProtocolBuilder httpProtocol() {
        return http
                .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
                .acceptHeader("application/json")
                .userAgentHeader("Gatling/MiniBank-LoadTest");
    }

    static ChainBuilder login() {
        return exec(
                http("Login - initiate")
                        .post("/auth/login/initiate")
                        .body(StringBody("{\"phone\":\"#{phone}\"}")).asJson()
                        .check(status().is(200))
                        .check(jsonPath("$.sessionId").saveAs("loginSessionId"))
                        .check(jsonPath("$.debugCode").saveAs("loginDebugCode"))
        ).exitHereIfFailed().exec(
                http("Login - confirm")
                        .post("/auth/login/confirm")
                        .body(StringBody("{\"sessionId\":\"#{loginSessionId}\",\"code\":\"#{loginDebugCode}\"}")).asJson()
                        .check(status().is(200))
                        .check(jsonPath("$.token").saveAs("sessionToken"))
        ).exitHereIfFailed();
    }

    static ChainBuilder internalTransfer() {
        return exec(
                http("Transfer - initiate")
                        .post("/transfers/internal/initiate")
                        .header("X-Session-Token", "#{sessionToken}")
                        .body(StringBody(
                                "{\"fromAccountId\":\"#{accountId}\",\"toAccountNumber\":\"#{toAccountNumber}\",\"amount\":10.00}"))
                        .asJson()
                        .check(status().is(200))
                        .check(jsonPath("$.transactionId").saveAs("transactionId"))
                        .check(jsonPath("$.debugCode").saveAs("transferDebugCode"))
        ).exitHereIfFailed().exec(
                http("Transfer - confirm")
                        .post("/transfers/internal/#{transactionId}/confirm")
                        .header("X-Session-Token", "#{sessionToken}")
                        .body(StringBody("{\"code\":\"#{transferDebugCode}\"}")).asJson()
                        .check(status().is(200))
        ).exitHereIfFailed().exec(
                http("Transfer - execute")
                        .post("/transfers/internal/#{transactionId}/execute")
                        .header("X-Session-Token", "#{sessionToken}")
                        .check(status().is(200))
        );
    }

    static ChainBuilder loginAndInternalTransfer() {
        return exec(login()).exec(internalTransfer());
    }
}
