import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public class Launcher {

  private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

  private final ServerWithHibernateReactive serverWithHibernateReactive;

  public Launcher(ServerWithHibernateReactive serverWithHibernateReactive) {
    this.serverWithHibernateReactive = serverWithHibernateReactive;
  }

  public void run() {
    LOG.info("🚀 Starting a PostgreSQL container");

    var postgreSQLContainer = new GenericContainer<>("postgres:14.3-alpine")
      .withExposedPorts(Constants.PG_PORT)
      .withEnv("POSTGRES_PASSWORD", Constants.PG_PASSWORD);

    postgreSQLContainer.start();

    LOG.info("🚀 Starting Vert.x");

    var vertx = Vertx.vertx();

    var options = new DeploymentOptions().setConfig(new JsonObject()
      .put("pgHost", postgreSQLContainer.getHost())
      .put("pgPort", postgreSQLContainer.getMappedPort(Constants.PG_PORT)));

    vertx.deployVerticle(serverWithHibernateReactive, options).onComplete(ar -> {
      if (ar.succeeded()) {
        LOG.info("✅ ServerWithHibernateReactive was deployed successfully");
      } else {
        LOG.error("🔥 ServerWithHibernateReactive deployment failed", ar.cause());
      }
    });
  }
}
