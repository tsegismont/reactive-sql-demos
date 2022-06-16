import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PgClientVerticle extends ApiVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(PgClientVerticle.class);

  private PgPool pgPool;

  @Override
  protected void initPool(String host, int port) {
    var connectOptions = new PgConnectOptions()
      .setHost(host)
      .setPort(port)
      .setDatabase(Constants.PG_DATABASE)
      .setUser(Constants.PG_USER)
      .setPassword(Constants.PG_PASSWORD);

    var poolOptions = new PoolOptions().setMaxSize(5);

    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
  }

  @Override
  protected Future<JsonArray> listProducts(RoutingContext rc) {
    LOG.info("listProducts");

    return pgPool.query("SELECT JSON_AGG(p) FROM Product p").execute()
      .map(rowset -> {
        var products = rowset.iterator().next().getJsonArray(0);
        return products != null ? products : new JsonArray();
      });
  }

  @Override
  protected Future<Product> createProduct(RoutingContext rc) {
    LOG.info("createProduct");

    var product = rc.body().asPojo(Product.class);

    var problems = Product.validate(product);
    if (!problems.isEmpty()) {
      return Future.failedFuture(problems.toString());
    }

    return pgPool.preparedQuery("INSERT INTO Product(name, price) VALUES ($1, $2) RETURNING id")
      .execute(Tuple.of(product.getName(), product.getPrice()))
      .map(rowSet -> {
        var keys = rowSet.iterator().next();
        product.setId(keys.getLong("id"));
        return product;
      });
  }

  @Override
  protected Future<Product> getProduct(RoutingContext rc) {
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

  public static void main(String[] args) {
    Launcher launcher = new Launcher(new PgClientVerticle());
    launcher.run();
  }
}
