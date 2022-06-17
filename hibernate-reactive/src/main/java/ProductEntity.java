import io.vertx.core.json.JsonArray;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.math.BigDecimal;

@Entity(name = "Product")
public class ProductEntity {

  @Id
  @GeneratedValue
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private BigDecimal price;

  public ProductEntity() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public static JsonArray validate(ProductEntity product) {
    JsonArray problems = new JsonArray();
    if (product == null) {
      problems.add("Product is null");
    } else {
      if (product.name == null || product.name.isBlank()) problems.add("Product name is missing");
      if (product.price == null || product.price.signum() < 0) problems.add("Product must have a positive price");
    }
    return problems;
  }

  @Override
  public String toString() {
    return "ProductEntity{" +
      "id=" + id +
      ", name='" + name + '\'' +
      ", price=" + price +
      '}';
  }
}
