package simulations


import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PersistenceSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080/api") // Here is the root for all relative URLs
    .acceptHeader("text/html,application/xhtml+xml,application/json,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
    
  val counter = new java.util.concurrent.atomic.AtomicInteger(2)

  val scn = scenario("Persistence Test") // A scenario is a chain of requests and pauses
    .exec(http("list_rules")
      .get("/rules")
    )
    .pause(1)
    .repeat(200)(
      exec(session => session.set("idx", "" + counter.getAndIncrement()))
      .exec(http("create_artifact")
        .post("/artifacts")
        .header("X-Registry-ArtifactId", "TestArtifact-${idx}")
        .body(StringBody("{ \"openapi\": \"3.0.2\", \"info\": { \"title\": \"Test Artifact ${idx}\"  } }"))
        .check(jsonPath("$.globalId").saveAs("globalId"))
        .check(jsonPath("$.id").saveAs("artifactId"))
      )
      .pause(5)
    )
    .exec(http("get_latest_version")
      .get("/artifacts/${artifactId}")
    )
    .pause(1)
    .exec(http("get_by_globalId")
      .get("/ids/${globalId}")
    )
    .pause(1)
    .exec(http("search_all")
      .get("/search/artifacts")
    )
    .pause(1)
    .exec(http("search_name_all")
      .get("/search/artifacts?search=Test&over=name")
    )
    .pause(1)
    .exec(http("search_name_subset")
      .get("/search/artifacts?search=7&over=name")
    )
    .pause(1)
    .repeat(50)(
      exec(http("search_name")
        .get("/search/artifacts?search=Artifact&over=name")
      )
      .pause(1)
      .exec(http("get_by_globalId")
        .get("/ids/${globalId}")
      )
    )

  setUp(
      scn.inject(rampUsers(200) during (60 seconds))
  ).protocols(httpProtocol)
}
