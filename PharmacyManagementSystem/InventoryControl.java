package PharmacyManagementSystem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** {@link InventoryControl} */
public class InventoryControl {
    private HashMap<UUID, Stock> stock;
    private HashMap<UUID, Stock> last_stock;
    private HashMap<UUID, Order> orders;
    private HashMap<UUID, Stock> last_order_items;
    private List<AutoOrder> auto_orders;

    public InventoryControl() {
        this.stock = new HashMap<UUID, Stock>();
        this.last_stock = new HashMap<UUID, Stock>();
        this.orders = new HashMap<UUID, Order>();
        this.last_order_items = new HashMap<UUID, Stock>();
        this.auto_orders = new ArrayList<>();
        Log.auditAnonymous("Inventory Control created.");
    }

    // Backend Updates API
    public void updateNoQuantity() {
        List<UUID> removals = new ArrayList<>();
        for (final UUID id : this.stock.keySet()) {
            if (stock.get(id).getQuantity() == 0) {
                removals.add(id);
            }
        }
        for (UUID id : removals) {
            this.stock.remove(id);
        }
    }

    /** */
    public void updateAutoOrders() {
        for (final AutoOrder auto_order : this.auto_orders) {
            List<Stock> new_order_items = new ArrayList<>();
            for (final Stock auto_stock : auto_order.getOrder().getOrderItems()) {
                UUID key = auto_stock.getID();
                MinStock order_stock = auto_order.getQuantities().get(key);

                final Stock inventory_stock = this.stock.get(key);
                int quantity = 0;
                if (inventory_stock != null) quantity = inventory_stock.getQuantity();
                if (quantity < order_stock.minimum_quantity() && !isStockOrdered(auto_stock)) {
                    Stock order_item = auto_stock.clone();

                    new_order_items.add(order_item);
                }
            }
            if (new_order_items.size() > 0) {
                final Order order = new Order(new_order_items);
                Log.audit("Creating order from auto orders: " + order);
                createUniqueOrder(order);
            }
        }
    }

    /** */
    public void updateDeliveries() {
        List<UUID> removals = new ArrayList<>();
        for (final UUID key : this.orders.keySet()) {
            final Order order = this.orders.get(key);
            if (order.getShipmentDate().isBefore(LocalDateTime.now())) {
                deliverOrder(order);
                removals.add(key);
            }
        }
        for (UUID key : removals) {
            this.orders.remove(key);
        }
    }

    /**
     * @return Returns a notification to be sent.
     */
    public List<Notification> updateExpired() {
        // TODO: Update expired prescriptions, stock items, and discounts
        List<Notification> notifications = new ArrayList<>();
        for (UUID key : this.getStock().keySet()) {
            Stock stock = this.getStock().get(key);
            if (stock instanceof Drug) {
                Drug drug = (Drug) stock;
                if (drug.getExpirationDate().isBefore(LocalDateTime.now())) {
                    notifications.add(
                            new Notification(
                                    PermissionLevel.PharmacyManager, "Drug is expired: " + drug));
                } else if (drug.getExpirationDate().isBefore(Config.expiredNotificationTime())) {
                    notifications.add(
                            new Notification(
                                    PermissionLevel.PharmacyManager,
                                    "Drug expires in less than 30 days: " + drug));
                }
                if (drug.getQuantity() < Config.minDrugQuantity()) {
                    notifications.add(
                            new Notification(
                                    PermissionLevel.PharmacyManager,
                                    "Drug has less than 120 items remaining: " + drug));
                }
            }
        }
        return notifications.size() == 0 ? null : notifications;
    }

    public Stock findStock(UUID id) {
        return this.stock.get(id);
    }

    // Getters/Setters
    public HashMap<UUID, Stock> getStock() {
        return stock;
    }

    public void setStock(final HashMap<UUID, Stock> inventory) {
        this.stock = inventory;
    }

    public HashMap<UUID, Stock> getLastStock() {
        return this.last_stock;
    }

    public void setLastStock(HashMap<UUID, Stock> last_stock) {
        this.last_stock = last_stock;
    }

    public HashMap<UUID, Stock> getLastOrderItems() {
        return last_order_items;
    }

    public void setLastOrderItems(HashMap<UUID, Stock> last_order_items) {
        this.last_order_items = last_order_items;
    }

    public HashMap<UUID, Order> getOrders() {
        return orders;
    }

    public void setOrders(final HashMap<UUID, Order> orders) {
        this.orders = orders;
    }

    public List<AutoOrder> getAutoOrders() {
        return auto_orders;
    }

    public void setAutoOrders(final List<AutoOrder> auto_orders) {
        this.auto_orders = auto_orders;
    }

    // Backend Stock API
    /**
     * @param item
     */
    @SuppressWarnings("unchecked")
    public void addStock(final Stock item) {
        Stock stock = this.stock.get(item.getID());

        if (stock == null) {
            this.stock.put(item.getID(), item);
        } else stock.setQuantity(stock.getQuantity() + item.getQuantity());
    }

    /**
     * @param item
     */
    @SuppressWarnings("unchecked")
    public void removeStock(final Stock item) {
        this.stock.remove(item.id);
    }

    @SuppressWarnings("unchecked")
    public Stock removeStock(UUID id) {
        return this.stock.remove(id);
    }

    // Backend Order API
    /**
     * @param order
     */
    public void addOrder(final Order order) {
        this.orders.put(order.getID(), order);
    }

    /**
     * @param auto_order
     */
    public void addAutoOrder(final AutoOrder auto_order) {
        this.auto_orders.add(auto_order);
    }

    /**
     * @param auto_order
     */
    public void removeAutoOrder(final AutoOrder auto_order) {
        this.auto_orders.remove(auto_order);
    }

    /**
     * @param order
     */
    @SuppressWarnings("unchecked")
    private void deliverOrder(final Order order) {
        this.last_stock = (HashMap<UUID, Stock>) this.stock.clone();
        this.last_order_items.clear();
        for (Stock item : order.getOrderItems()) {
            this.last_order_items.put(item.getID(), item);
        }
        for (final Stock order_stock : order.getOrderItems()) {
            Stock inventory_stock = this.stock.get(order_stock.getID());
            if (inventory_stock == null) {
                addStock(order_stock.clone());
            } else if (inventory_stock instanceof Drug) {
                // For Drug we cannot increase quantity due to expiration dates
                addStock(order_stock.clone());
            } else {
                Stock new_stock = order_stock.clone();
                new_stock.setQuantity(new_stock.getQuantity() + inventory_stock.getQuantity());
                addStock(new_stock);
            }
        }
    }

    /**
     * @param new_order
     */
    private void createUniqueOrder(final Order new_order) {
        for (UUID key : this.orders.keySet()) {
            Order order = this.orders.get(key);
            for (Stock order_item : order.getOrderItems()) {
                for (final Stock new_item : new_order.getOrderItems()) {
                    if (new_item.getID().equals(order_item.getID())) {
                        // Return early if the item is already ordered
                        return;
                    }
                }
            }
        }

        Log.audit("New unique order: " + new_order);
        addOrder(new_order);
    }

    private boolean isStockOrdered(Stock ordered_stock) {
        for (UUID key : this.orders.keySet()) {
            for (final Stock stock : this.orders.get(key).getOrderItems()) {
                if (stock.getID().equals(ordered_stock.getID())) {
                    return true;
                }
            }
        }
        return false;
    }
}

enum DiscountType {
    FlatDiscount,
    PercentDiscount,
}

class Discount {
    protected double discount;
    protected LocalDateTime expiration;

    /**
     * @param discount
     */
    Discount(final double discount, final LocalDateTime expiration) {
        this.discount = discount;
        this.expiration = expiration;
        Log.audit("Discount created.");
    }

    protected boolean isExpired() {
        return this.expiration.isAfter(LocalDateTime.now());
    }

    /**
     * @param price
     * @return
     */
    public double getDiscount(final double price) {
        if (isExpired()) return price;
        if (price < discount) return 0;

        return price - discount;
    }

    @Override
    public String toString() {
        return "[Discount: $" + this.discount + ", Expiration: " + this.expiration + "]";
    }
}

class PercentDiscount extends Discount {
    /**
     * Constructs a {@code PercentDiscount} instance with the specified discount value.
     *
     * <p>The discount value should be a decimal between 0.0 and 1.0, inclusive. If the discount is
     * greater than 1.0, an {@link IllegalArgumentException} will be thrown.
     *
     * @param discount A decimal value representing the discount. Must be between 0.0 and 1.0
     *     inclusive.
     * @throws IllegalArgumentException if the discount is greater than 1.0 or less than 0.0.
     */
    PercentDiscount(final double discount, final LocalDateTime expiration) {
        super(discount, expiration);
        if (discount > 1.0 || discount < 0.0)
            throw new IllegalArgumentException("Percent discount out of range (0.0-1.0)");
    }

    /**
     * @param price
     * @return
     */
    @Override
    public double getDiscount(final double price) {
        if (isExpired()) return price;

        return price * (1.0 - discount);
    }

    @Override
    public String toString() {
        return "[Discount: " + this.discount + "%, Expiration: " + this.expiration + "]";
    }
}

enum StockType {
    Stock,
    Drug,
}

class Stock implements Cloneable {
    // Data Members
    protected UUID id;
    protected int quantity;
    protected double price;
    protected String name;
    protected Discount discount;

    // Constructors
    /**
     * @param quantity
     * @param price
     * @param name
     * @param discount
     */
    public Stock(
            final int quantity, final double price, final String name, final Discount discount) {
        this.quantity = quantity;
        this.price = price;
        this.discount = discount;
        this.name = name;
        createID();
        Log.audit("Stock created: " + this);
    }

    // Getters/Setters
    public UUID getID() {
        return id;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(final int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        if (this.discount == null) return this.price;
        else if (this.discount.expiration.isBefore(LocalDateTime.now())) {
            return this.price;
        } else {
            return this.discount.getDiscount(this.price);
        }
    }

    public void setPrice(final double price) {
        this.price = price;
    }

    public Discount getDiscount() {
        return discount;
    }

    public void setDiscount(final Discount discount) {
        this.discount = discount;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
        createID();
    }

    // Override Methods
    @Override
    public Stock clone() {
        try {
            Log.audit("Cloning stock: " + this);
            return (Stock) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError("Clone not supposed for " + this.getClass().getName());
        }
    }

    @Override
    public String toString() {
        return "[ID: "
                + this.getID()
                + ", Quantity: "
                + this.getQuantity()
                + ", Price: "
                + this.getPrice()
                + ", Discount: "
                + this.getDiscount()
                + ", Name: "
                + this.getName()
                + "]";
    }

    private void createID() {
        InventoryControl inventory = Backend.get().inventory;
        Stock stock = (Stock) inventory.removeStock(this.id);

        this.id = UUID.nameUUIDFromBytes(name.getBytes());
        if (stock != null) inventory.addStock(stock);
    }
}

class Drug extends Stock {
    // Data Members
    private boolean is_controlled;
    private String drug_name;
    private LocalDateTime expiration_date;

    // Constructors
    /**
     * @param quantity
     * @param price
     * @param name
     * @param discount
     * @param is_controlled
     * @param drug_name
     * @param expiration_date
     */
    public Drug(
            final int quantity,
            final double price,
            final String name,
            final Discount discount,
            final boolean is_controlled,
            final String drug_name,
            final LocalDateTime expiration_date) {
        super(quantity, price, name, discount);
        this.is_controlled = is_controlled;
        this.drug_name = drug_name;
        this.expiration_date = expiration_date;
        createID();
    }

    // Getters/Setters
    public boolean getIsControlled() {
        return this.is_controlled;
    }

    public void setIsControlled(final boolean is_controlled) {
        this.is_controlled = is_controlled;
    }

    public String getDrugName() {
        return this.drug_name;
    }

    public void setDrugName(final String drug_name) {
        this.drug_name = drug_name;
        createID();
    }

    public LocalDateTime getExpirationDate() {
        return this.expiration_date;
    }

    public void setExpirationDate(final LocalDateTime expiration_date) {
        this.expiration_date = expiration_date;
        createID();
    }

    // Override Methods
    @Override
    public Drug clone() {
        return (Drug) super.clone();
    }

    @Override
    public String toString() {
        return super.toString()
                + "-[Controlled: "
                + this.getIsControlled()
                + ", Drug Name: "
                + this.getDrugName()
                + ", Expiration Date: "
                + this.getExpirationDate()
                + "]";
    }

    private void createID() {
        InventoryControl inventory = Backend.get().inventory;
        Drug drug = (Drug) inventory.removeStock(this.id);
        this.id = UUID.nameUUIDFromBytes((name + drug_name + expiration_date).getBytes());
        if (drug != null) inventory.addStock(drug);
    }
}

class Order {
    // Data Members
    private UUID order_id;
    private List<Stock> order_items;
    private LocalDateTime shipment_date;

    // Constructors
    /**
     * @param order_items
     */
    public Order(final List<Stock> order_items) {
        this.order_id = UUID.randomUUID();
        this.order_items = order_items;
        this.shipment_date = Config.orderDeliveryTime();
        Log.audit("Order created: " + this);
    }

    /**
     * @param order_item
     */
    public Order(final Stock order_item) {
        this.order_id = UUID.randomUUID();
        this.shipment_date = Config.orderDeliveryTime();

        final List<Stock> order_items = new ArrayList<>();
        order_items.add(order_item);
        this.order_items = order_items;
        Log.audit("Order created: " + this);
    }

    // Getters/Setters
    public UUID getID() {
        return this.order_id;
    }

    public List<Stock> getOrderItems() {
        return this.order_items;
    }

    public LocalDateTime getShipmentDate() {
        return this.shipment_date;
    }

    public void setShipmentDate(LocalDateTime shipment_date) {
        this.shipment_date = shipment_date;
    }

    public void addItem(final Stock item) {
        this.order_items.add(item);
    }

    // Override Methods
    @Override
    public String toString() {
        return "[Order ID: "
                + this.order_id
                + ", Shipment Date: "
                + this.shipment_date
                + ", Items: "
                + this.order_items
                + "]";
    }
}

record MinStock(int minimum_quantity, int order_quantity) {}

// Restock request
class AutoOrder {
    private UUID id;
    private HashMap<UUID, MinStock> quantities;
    private Order order;

    AutoOrder(HashMap<UUID, MinStock> quantities, Order order) {
        this.id = UUID.randomUUID();
        this.quantities = quantities;
        this.order = order;
        Log.audit("Auto order created: " + this);
    }

    public UUID getID() {
        return id;
    }

    public HashMap<UUID, MinStock> getQuantities() {
        return this.quantities;
    }

    public void setQuantities(HashMap<UUID, MinStock> quantities) {
        this.quantities = quantities;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "[ID: "
                + this.id
                + ", Quantities: "
                + this.quantities
                + ", Items: "
                + this.order
                + "]";
    }
}
