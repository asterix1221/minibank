package gatling.simulations;

import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Test 2 (spec Part 2): fixed load at (by default) targetUsersPerSec, held for
 * durationMinutes, to confirm the system holds up over time rather than just in a brief
 * spike. targetUsersPerSec should be set to ~70-80% of whatever MaxSearchSimulation found -
 * see README for the actual number found on a real run.
 *
 * Stability is judged by comparing the first few minutes of the run against the last few,
 * both in the Gatling HTML report's over-time charts and in the Grafana timeline (see
 * README Part 3) - error rate should not grow and p95 should not drift upward.
 *
 * Run:
 *   mvn gatling:test -Dgatling.simulationClass=gatling.simulations.ConfirmationSimulation \
 *       -DbaseUrl=http://localhost:8080 -DtargetUsersPerSec=8 -DdurationMinutes=20 \
 *       -DmaxFailedPercent=1.0 -Dp95ThresholdMs=1000
 */
public class ConfirmationSimulation extends Simulation {

    private static final int TARGET_USERS_PER_SEC = Integer.getInteger("targetUsersPerSec", 8);
    private static final int DURATION_MINUTES = Integer.getInteger("durationMinutes", 20);
    private static final double MAX_FAILED_PERCENT =
            Double.parseDouble(System.getProperty("maxFailedPercent", "1.0"));
    private static final int P95_THRESHOLD_MS = Integer.getInteger("p95ThresholdMs", 1000);

    private final FeederBuilder<String> feeder = csv("data/clients.csv").circular();

    private final ScenarioBuilder scn = scenario("Confirmation - sustained internal transfer load")
            .feed(feeder)
            .exec(MiniBankChains.loginAndInternalTransfer());

    {
        setUp(
                scn.injectOpen(
                        constantUsersPerSec(TARGET_USERS_PER_SEC).during(Duration.ofMinutes(DURATION_MINUTES))
                )
        ).protocols(MiniBankChains.httpProtocol())
         .assertions(
                global().failedRequests().percent().lte(MAX_FAILED_PERCENT),
                global().responseTime().percentile(95).lte(P95_THRESHOLD_MS)
         );
    }
}
