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
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

public class ServerWithJdbc extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(ServerWithJdbc.class);

  private JDBCPool jdbcPool;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    var host = config().getString("pgHost", "localhost");
    int port = config().getInteger("pgPort", 5432);

    initPool(host, port);

    var router = Router.router(vertx);

    var bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler);

    router.get("/products").respond(this::listProducts);
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

  private void initPool(String host, int port) {
    var connectOptions = new JDBCConnectOptions()
      .setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, Constants.PG_DATABASE))
      .setUser(Constants.PG_USER)
      .setPassword(Constants.PG_PASSWORD);

    var poolOptions = new PoolOptions().setMaxSize(5);

    jdbcPool = JDBCPool.pool(vertx, connectOptions, poolOptions);
  }

  private Future<JsonArray> listProducts(RoutingContext rc) {
    LOG.info("listProducts");

    return jdbcPool.query("SELECT * FROM Product").execute()
      .map(rowset -> {
        var products = new ArrayList<>(rowset.size());
        for (Row row : rowset) {
          products.add(row.toJson());
        }
        return new JsonArray(products);
      });
  }

  private Future<Product> createProduct(RoutingContext rc) {
    LOG.info("createProduct");

    var product = rc.getBodyAsJson().mapTo(Product.class);

    var problems = Product.validate(product);
    if (!problems.isEmpty()) {
      return Future.failedFuture(problems.toString());
    }

    return jdbcPool.preparedQuery("INSERT INTO Product(name, price) VALUES (?, ?)")
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

    return SqlTemplate.forQuery(jdbcPool, "SELECT * FROM Product WHERE id=#{id}")
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

  public static void main(String[] args) {
    Launcher launcher = new Launcher(new ServerWithJdbc());
    launcher.run();
  }
}
