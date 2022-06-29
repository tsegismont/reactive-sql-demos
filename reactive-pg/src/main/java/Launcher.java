import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

public class Launcher {

  private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

  private final ServerWithPgClient serverWithPgClient;

  public Launcher(ServerWithPgClient serverWithPgClient) {
    this.serverWithPgClient = serverWithPgClient;
  }

  public void run() {
    LOG.info("🚀 Starting a PostgreSQL container");

    var postgreSQLContainer = new GenericContainer<>("postgres:14.3-alpine")
      .withExposedPorts(Constants.PG_PORT)
      .withEnv("POSTGRES_PASSWORD", Constants.PG_PASSWORD)
      .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY);

    postgreSQLContainer.start();

    LOG.info("🚀 Starting Vert.x");

    var vertx = Vertx.vertx();

    var options = new DeploymentOptions().setConfig(new JsonObject()
      .put("pgHost", postgreSQLContainer.getHost())
      .put("pgPort", postgreSQLContainer.getMappedPort(Constants.PG_PORT)));

    vertx.deployVerticle(serverWithPgClient, options).onComplete(ar -> {
      if (ar.succeeded()) {
        LOG.info("✅ ServerWithPgClient was deployed successfully");
      } else {
        LOG.error("🔥 ServerWithPgClient deployment failed", ar.cause());
      }
    });
  }
}
