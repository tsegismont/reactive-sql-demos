import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ApiVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(ApiVerticle.class);

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
      .onSuccess(server -> LOG.info("HTTP server listening on port 8080"))
      .onComplete(startPromise);
  }

  protected abstract void initPool(String host, int port);

  protected abstract Future<JsonArray> listProducts(RoutingContext rc);

  protected abstract Future<Product> createProduct(RoutingContext rc);

  protected abstract Future<Product> getProduct(RoutingContext rc);
}

