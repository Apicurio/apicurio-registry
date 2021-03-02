package simulations


import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class InitialLoadSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080/apis") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/json,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
    
  val counter = new java.util.concurrent.atomic.AtomicInteger(2)

  val scn = scenario("Initial Load Test") // A scenario is a chain of requests and pauses
    .exec(http("list_rules")
      .get("/rules")
    )
    .pause(1)
    .repeat(300)(
      exec(session => session.set("idx", "" + counter.getAndIncrement()))
      .exec(http("create_artifact")
        .post("/registry/v1/artifacts")
        .header("X-Registry-ArtifactId", "TestArtifact-5-${idx}")
        .body(StringBody("{ \"openapi\": \"3.0.2\", \"info\": { \"title\": \"Test Artifact ${idx}\"  } }"))
        .check(jsonPath("$.globalId").saveAs("globalId"))
        .check(jsonPath("$.id").saveAs("artifactId"))
      )
      .pause(1)
    )

  setUp(
      scn.inject(rampUsers(200) during (60 seconds))
  ).protocols(httpProtocol)
}
