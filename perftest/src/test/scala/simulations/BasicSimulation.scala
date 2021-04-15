package simulations


import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._


class BasicSimulation extends Simulation {

  val registryUrl = scala.util.Properties.envOrElse("REGISTRY_URL", "http://localhost:8080/apis/registry/v1")
  val users = scala.util.Properties.envOrElse("TEST_USERS", "10").toInt
  val ramp = scala.util.Properties.envOrElse("TEST_RAMP_TIME", "30").toInt
  

  val httpProtocol = http
    .baseUrl(registryUrl) // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/json,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val scn = scenario("Smoke Test") // A scenario is a chain of requests and pauses
    .exec(http("list_artifacts")
      .get("/artifacts")
    )
    .pause(1)
    .exec(http("create_artifact")
      .post("/artifacts")
      .body(StringBody("{ \"openapi\": \"3.0.2\" }"))
      .check(jsonPath("$.id").saveAs("artifactId"))
      .check(jsonPath("$.globalId").saveAs("globalId"))
    )
    .pause(1)
    .exec(http("get_latest_version")
      .get("/artifacts/${artifactId}")
    )
    .pause(1)
    .exec(http("get_globalId")
      .get("/ids/${globalId}")
    )

  setUp(
      scn.inject(rampUsers(users) during (ramp seconds))
  ).protocols(httpProtocol)
}
