public abstract class Product {
    String name;
    double orignalprice;
    ProductType type;

    Product(){};

    Product(String name, double price, ProductType type){
        this.name =name;
        this.orignalprice=price;
        this.type = type;
    }

    public abstract double getPrice();

    public ProductType getType(){
        return this.type;
    }
    
}
