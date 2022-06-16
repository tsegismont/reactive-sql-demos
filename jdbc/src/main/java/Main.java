import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.util.logging.Level;

public class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {

    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
    java.util.logging.Logger.getLogger("").setLevel(Level.FINEST);

    LOG.info("🚀 Starting a PostgreSQL container");

    var postgreSQLContainer = new GenericContainer<>("postgres:14.3-alpine")
      .withExposedPorts(5432)
      .withEnv("POSTGRES_PASSWORD", "vertx-in-action")
      .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY);

    postgreSQLContainer.start();

    LOG.info("🚀 Starting Vert.x");

    var vertx = Vertx.vertx();

    var options = new DeploymentOptions().setConfig(new JsonObject()
      .put("pgHost", postgreSQLContainer.getHost())
      .put("pgPort", postgreSQLContainer.getMappedPort(5432)));

    vertx.deployVerticle(new ApiVerticle(), options).onComplete(ar -> {
      if (ar.succeeded()) {

        LOG.info("✅ ApiVerticle was deployed successfully");
      } else {
        LOG.error("🔥 ApiVerticle deployment failed", ar.cause());
      }
    });
  }
}
