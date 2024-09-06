import java.util.ArrayList;
import java.util.List;

public class ShoppingCart {

    List<Product> productList;

    public ShoppingCart(){
        productList = new ArrayList<>();
    }

    public void addtocart(){

    }

    public int getTotalPriceOfCart(){
        int totalPrice=0;
        for(Product p: productList){
            totalPrice += p.getPrice();  //here
        }
        return totalPrice;
    }
}
