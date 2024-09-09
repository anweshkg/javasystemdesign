import java.util.ArrayList;
import java.util.List;

public class TypeCouponDecorator extends CouponDecorator{
    Product product;
    int typeDiscountPercentage;
    ProductType type;
    static List<ProductType> eligibleTypes = new ArrayList<>(); //here
    static {eligibleTypes.add(ProductType.Food);}

    public TypeCouponDecorator(Product product, int typeDiscountPercentage, ProductType type){
        this.product = product;
        this.typeDiscountPercentage= typeDiscountPercentage;
        this.type=type;
    }

    @Override
    public double getPrice(){
        double price = product.getPrice();
        if(eligibleTypes.contains(type)){
            return price - (price*typeDiscountPercentage)/100;
        }
        return price;
    }
}