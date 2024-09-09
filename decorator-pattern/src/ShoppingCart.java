import java.util.ArrayList;
import java.util.List;

public class ShoppingCart {

    List<Product> productList;

    public ShoppingCart(){
        productList = new ArrayList<>();
    }

    public void addtocart(Product product){
        Product productWithEligibleDiscount= new TypeCouponDecorator(new PercentageCouponDecorator(product, 10), 5, product.getType());
        productList.add(productWithEligibleDiscount);
    }

    public double getTotalPriceOfCart(){
        double totalPrice=0;
        for(Product p: productList){
            totalPrice += p.getPrice();  //here
        }
        return totalPrice;
    }
}
