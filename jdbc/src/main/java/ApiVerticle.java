import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ApiVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(ApiVerticle.class);

  private JDBCPool pgPool;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var host = config().getString("pgHost", "localhost");
    int port = config().getInteger("pgPort", 5432);

    var connectOptions = new JDBCConnectOptions()
      .setJdbcUrl(String.format("jdbc:postgresql://%s:%s/postgres", host, port))
      .setUser("postgres")
      .setPassword("vertx-in-action");

    var poolOptions = new PoolOptions().setMaxSize(5);

    pgPool = JDBCPool.pool(vertx, connectOptions, poolOptions);

    var router = Router.router(vertx);

    var bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler);

    router.get("/products").respond(rc -> listProducts());
    router.get("/products/:id").respond(this::getProduct);
    router.post("/products").respond(this::createProduct);
    router.route().failureHandler(ErrorHandler.create(vertx, true));

    var future = vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080);

    future
      .<Void>mapEmpty()
      .onSuccess(__ -> LOG.info("HTTP server listening on port 8080"))
      .onComplete(startPromise);
  }

  private Future<JsonArray> listProducts() {
    LOG.info("listProducts");

    return pgPool.query("SELECT JSON_AGG(p) FROM Product p").execute()
      .map(rowset -> {
        var products = rowset.iterator().next().getJsonArray(0);
        return products != null ? products : new JsonArray();
      });
  }

  private Future<Product> createProduct(RoutingContext rc) {
    LOG.info("createProduct");

    var product = rc.body().asPojo(Product.class);

    var problems = Product.validate(product);
    if (!problems.isEmpty()) {
      return Future.failedFuture(problems.toString());
    }

    return pgPool.preparedQuery("INSERT INTO Product(name, price) VALUES (?, ?)")
      .execute(Tuple.of(product.getName(), product.getPrice()))
      .map(rowSet -> {
        var keys = rowSet.property(JDBCPool.GENERATED_KEYS);
        product.setId(keys.getLong("id"));
        return product;
      });
  }

  private Future<Product> getProduct(RoutingContext rc) {
    LOG.info("getProduct");

    var id = Long.valueOf(rc.pathParam("id"));

    return SqlTemplate.forQuery(pgPool, "SELECT * FROM Product WHERE id=#{id}")
      .mapTo(Product.class)
      .execute(Map.of("id", id))
      .map(rows -> {
        var iterator = rows.iterator();
        if (iterator.hasNext()) {
          return iterator.next();
        } else {
          return null;
        }
      });
  }
}

