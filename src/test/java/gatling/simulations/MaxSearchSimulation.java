package gatling.simulations;

import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Test 1 (spec Part 2): open-model load, stepping up every stepDurationSeconds until the
 * SLA assertions (error rate, p95) fail for the first time. That step is the discovered
 * ceiling - read it off the console output / HTML report and record it in README.
 *
 * Every parameter is a system property (-Dname=value) on purpose - numbers tuned for one
 * machine are meaningless on another. Defaults below are a reasonable starting point for a
 * single dev machine and were NOT calibrated against a real run (no Docker/network in the
 * sandbox this was written in - see README).
 *
 * Run:
 *   mvn gatling:test -Dgatling.simulationClass=gatling.simulations.MaxSearchSimulation \
 *       -DbaseUrl=http://localhost:8080 -DstartUsersPerSec=2 -DusersPerSecIncrement=2 \
 *       -Dsteps=15 -DstepDurationSeconds=30 -DrampDurationSeconds=5 \
 *       -DmaxFailedPercent=1.0 -Dp95ThresholdMs=1000
 */
public class MaxSearchSimulation extends Simulation {

    private static final int START_USERS_PER_SEC = Integer.getInteger("startUsersPerSec", 2);
    private static final int USERS_PER_SEC_INCREMENT = Integer.getInteger("usersPerSecIncrement", 2);
    private static final int STEPS = Integer.getInteger("steps", 15);
    private static final int STEP_DURATION_SECONDS = Integer.getInteger("stepDurationSeconds", 30);
    private static final int RAMP_DURATION_SECONDS = Integer.getInteger("rampDurationSeconds", 5);
    private static final double MAX_FAILED_PERCENT =
            Double.parseDouble(System.getProperty("maxFailedPercent", "1.0"));
    private static final int P95_THRESHOLD_MS = Integer.getInteger("p95ThresholdMs", 1000);

    private final FeederBuilder<String> feeder = csv("data/clients.csv").circular();

    private final ScenarioBuilder scn = scenario("Max search - internal transfer")
            .feed(feeder)
            .exec(MiniBankChains.loginAndInternalTransfer());

    {
        setUp(
                scn.injectOpen(
                        incrementUsersPerSec(USERS_PER_SEC_INCREMENT)
                                .times(STEPS)
                                .eachLevelLasting(Duration.ofSeconds(STEP_DURATION_SECONDS))
                                .separatedByRampsLasting(Duration.ofSeconds(RAMP_DURATION_SECONDS))
                                .startingFrom(START_USERS_PER_SEC)
                )
        ).protocols(MiniBankChains.httpProtocol())
         .assertions(
                global().failedRequests().percent().lte(MAX_FAILED_PERCENT),
                global().responseTime().percentile(95).lte(P95_THRESHOLD_MS)
         );
    }
}
