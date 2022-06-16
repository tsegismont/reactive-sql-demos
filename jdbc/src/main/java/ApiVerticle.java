import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static java.util.Objects.requireNonNull;

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

    var execute = pgPool.query("SELECT * FROM Product")
      .execute();

    return execute
      .map(rowset -> {
        var data = new JsonArray();
        for (var row : rowset) {
          data.add(row.toJson());
        }
        return data;
      })
      .otherwise(new JsonArray());
  }

  private Future<JsonObject> createProduct(RoutingContext rc) {
    LOG.info("createProduct");

    var json = rc.body().asJsonObject();
    String name;
    BigDecimal price;

    try {
      requireNonNull(json, "The incoming JSON document cannot be null");
      name = requireNonNull(json.getString("name"), "The product name cannot be null");
      price = new BigDecimal(json.getString("price"));
    } catch (Throwable err) {
      LOG.error("Could not extract values", err);
      return Future.failedFuture(err);
    }

    return pgPool.preparedQuery("INSERT INTO Product(name, price) VALUES (?, ?)")
      .execute(Tuple.of(name, price))
      .map(rowSet -> {
        var row = rowSet.property(JDBCPool.GENERATED_KEYS);
        return new JsonObject()
          .put("id", row.getInteger("id"))
          .put("name", name)
          .put("price", price);
      })
      .onFailure(err -> LOG.error("Woops", err));
  }

  private Future<JsonObject> getProduct(RoutingContext rc) {
    LOG.info("getProduct");
    var id = Long.valueOf(rc.pathParam("id"));

    return pgPool
      .preparedQuery("SELECT * FROM Product WHERE id=?")
      .execute(Tuple.of(id))
      .map(rows -> {
        var iterator = rows.iterator();
        if (iterator.hasNext()) {
          return iterator.next().toJson();
        } else {
          return new JsonObject();
        }
      })
      .onFailure(err -> LOG.error("Woops", err));
  }

}

