public class Item1 extends Product {
    Item1(String name, double price, ProductType type){
        super(name, price, type);
    }

    @Override
    public double getPrice(){
        return this.orignalprice;
    }
}
