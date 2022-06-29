import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.util.logging.Level;

public class Launcher {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
    java.util.logging.Logger.getLogger("").setLevel(Level.FINEST);
  }

  private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

  private final ServerWithJdbc serverWithJdbc;

  public Launcher(ServerWithJdbc serverWithJdbc) {
    this.serverWithJdbc = serverWithJdbc;
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

    vertx.deployVerticle(serverWithJdbc, options).onComplete(ar -> {
      if (ar.succeeded()) {
        LOG.info("✅ ServerWithJdbc was deployed successfully");
      } else {
        LOG.error("🔥 ServerWithJdbc deployment failed", ar.cause());
      }
    });
  }
}
