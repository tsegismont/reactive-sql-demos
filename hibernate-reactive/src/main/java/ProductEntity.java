import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity(name = "Product")
public class ProductEntity {

  @Id
  @GeneratedValue
  private Long id;

  @Column(nullable = false)
  @NotEmpty
  private String name;

  @Column(nullable = false)
  @NotNull
  @DecimalMin("0.0")
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

  @Override
  public String toString() {
    return "ProductEntity{" +
      "id=" + id +
      ", name='" + name + '\'' +
      ", price=" + price +
      '}';
  }
}
