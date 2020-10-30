package simulations


import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

// This simulation assumes there are 100k artifacts already added to the storage.
class CrashServerSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080/api") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/json,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val rando = new java.security.SecureRandom()

  val scn = scenario("Crash Server Test") // A scenario is a chain of requests and pauses
    .repeat(100)(
      exec(session => session.set("idx", "" + (rando.nextInt(90000) + 100)))
      .exec(http("get_latest_version")
        .get("/artifacts/TestArtifact-${idx}")
      )
      .pause(1)

      .exec(http("get_latest_metadata")
        .get("/artifacts/TestArtifact-${idx}/meta")
        .check(jsonPath("$.globalId").saveAs("globalId"))
      )
      .pause(1)

      .exec(http("get_by_globalId")
        .get("/ids/${globalId}")
      )
      .pause(1)
      .exec(http("get_by_version")
        .get("/artifacts/TestArtifact-${idx}/versions/1")
      )
      .pause(1)
    )

  setUp(
      scn.inject(rampUsers(2000) during (900 seconds))
  ).protocols(httpProtocol)
}
