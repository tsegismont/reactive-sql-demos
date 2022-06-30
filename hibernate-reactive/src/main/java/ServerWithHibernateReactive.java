import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import io.vertx.mutiny.ext.web.handler.ErrorHandler;
import org.hibernate.reactive.mutiny.Mutiny;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Persistence;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;

public class ServerWithHibernateReactive extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(ServerWithHibernateReactive.class);

  private final Validator validator;

  private Mutiny.SessionFactory emf;

  public ServerWithHibernateReactive() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Override
  public Uni<Void> asyncStart() {
    var host = config().getString("pgHost", "localhost");
    int port = config().getInteger("pgPort", 5432);

    var hibernateStart = vertx.executeBlocking(initHibernate(host, port));

    var router = Router.router(vertx);

    var bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler);

    router.get("/products").respond(this::listProducts);
    router.get("/products/:id").respond(this::getProduct);
    router.post("/products").respond(this::createProduct);
    router.route().failureHandler(ErrorHandler.create(vertx, true));

    var httpServerStart = vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080);

    return httpServerStart
      .chain(() -> hibernateStart)
      .onItem().invoke(() -> LOG.info("HTTP server listening on port 8080"));
  }

  private Uni<Void> initHibernate(String host, int port) {
    return Uni.createFrom().deferred(() -> {
      var props = Map.of(
        "javax.persistence.jdbc.url", String.format("jdbc:postgresql://%s:%s/%s", host, port, Constants.PG_DATABASE),
        "javax.persistence.jdbc.user", Constants.PG_USER,
        "javax.persistence.jdbc.password", Constants.PG_PASSWORD,
        "hibernate.connection.pool_size", 5
      );
      emf = Persistence
        .createEntityManagerFactory("pg-demo", props)
        .unwrap(Mutiny.SessionFactory.class);

      LOG.info("Hibernate Reactive has started");
      return Uni.createFrom().voidItem();
    });
  }

  private Uni<List<ProductEntity>> listProducts(RoutingContext rc) {
    LOG.info("listProducts");

    return emf.withSession(session -> session.createQuery("from Product", ProductEntity.class).getResultList());
  }

  private Uni<ProductEntity> createProduct(RoutingContext rc) {
    LOG.info("createProduct");

    var product = rc.getBodyAsJson().mapTo(ProductEntity.class);

    var problems = validator.validate(product);
    if (!problems.isEmpty()) {
      return Uni.createFrom().failure(new NoStackTraceThrowable(problems.toString()));
    }

    return emf.withSession(session -> session
      .persist(product)
      .chain(session::flush)
      .replaceWith(product));
  }

  private Uni<ProductEntity> getProduct(RoutingContext rc) {
    LOG.info("getProduct");

    var id = Long.valueOf(rc.pathParam("id"));

    return emf.withSession(session -> session.find(ProductEntity.class, id));
  }

  public static void main(String[] args) {
    Launcher launcher = new Launcher(new ServerWithHibernateReactive());
    launcher.run();
  }
}
