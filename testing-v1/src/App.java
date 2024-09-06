public class App {
    public static void main(String[] args) throws Exception {
        Product item1= new Item1("dosa", 100, ProductType.Food);
        ShoppingCart cart = new ShoppingCart();
        cart.addtocart(item1);
        System.out.println(cart.getTotalPriceOfCart());
    }
}
