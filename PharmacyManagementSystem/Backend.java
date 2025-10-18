package PharmacyManagementSystem;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The {@link Backend} is a singleton class that is responsible for managing the core functionality
 * of the Pharmacy Management system. The {@link #get()} method is used to retrieve the backend
 * instance.
 */
public class Backend {
    public InventoryControl inventory;

    private static Backend backend;

    private HashMap<UUID, Account> accounts;
    private HashMap<UUID, Customer> customers;
    private List<Stock> last_purchase;

    private Account logging_in;
    private Account logged_in;

    Backend() {
        this.inventory = new InventoryControl();
        this.accounts = new HashMap<UUID, Account>();
        this.customers = new HashMap<UUID, Customer>();
        this.last_purchase = new ArrayList<Stock>();
        Log.auditAnonymous("Backend initialized.");

        initAdmin();
    }

    public static Backend get() {
        if (backend == null) {
            backend = new Backend();
        }
        return backend;
    }

    public void update() {
        this.inventory.updateAutoOrders();
        this.inventory.updateDeliveries();
        sendNotification(this.inventory.updateExpired());
        updateCustomers();
        this.inventory.updateNoQuantity();
        updateDiscrepancies();
    }

    public HashMap<UUID, Account> getAccounts() {
        return accounts;
    }

    public HashMap<UUID, Customer> getCustomers() {
        return customers;
    }

    public Account getLoggingIn() {
        return logging_in;
    }

    public Account getLoggedIn() {
        return logged_in;
    }

    public void login(String data) {
        UUID key = UUID.nameUUIDFromBytes(data.getBytes());
        if (this.logging_in.getPassword().equals(key)) {
            setLoggedIn(this.logging_in);
            setLoggingIn(null);
            this.logged_in.setFailureAttempts(0);

            Log.tui(
                    "Welcome "
                            + this.logged_in.getName()
                            + ". You are "
                            + this.logged_in.getAge()
                            + " years old.");
            this.logged_in.printNotifications();
        } else {
            loginFailed(this.logging_in);
            setLoggingIn(null);

            Log.info("Login failed.");
        }
    }

    public Response logout() {
        this.setLoggedIn(null);
        Log.info("Logged out.");

        return Response.Ok;
    }

    /**
     * @param account
     */
    public Response createAccount(final Account account) {
        if (this.accounts.containsKey(account.getLogin())) return Response.Forbidden;
        this.accounts.put(account.getLogin(), account);
        Log.tui("Account created: " + account);

        return Response.Ok;
    }

    public Response createDiscount(final List<Object> data) {
        Discount discount = (Discount) data.get(0);

        Stock item = this.inventory.getStock().get((UUID) data.get(1));
        if (item == null) {
            Log.tui("Discount creation failed.");
            return Response.NotFound;
        }

        item.setDiscount(discount);
        Log.tui(discount.getClass().getName() + " created: " + discount);

        return Response.Ok;
    }

    public Response createCustomer(final Customer customer) {
        if (customer == null) return Response.BadRequest;
        this.customers.put(customer.getID(), customer);

        return Response.Ok;
    }

    public Response createPrescription(final List<Object> data) {
        if (data == null) return Response.BadRequest;

        Patient customer = (Patient) this.customers.get(data.get(0));
        if (customer == null) return Response.BadRequest;
        customer.addPrescription((Prescription) data.get(1));

        return Response.Ok;
    }

    public Response createOrder(final Order order) {
        if (order == null) return Response.BadRequest;
        this.inventory.addOrder(order);

        return Response.Ok;
    }

    public Response createAutoOrder(final AutoOrder order) {
        if (order == null) return Response.BadRequest;
        this.inventory.addAutoOrder(order);

        return Response.Ok;
    }

    public Response changePassword(final String data) {
        if (this.getLoggedIn().getPassword().equals((String) data)) {
            return Response.NewPassword;
        } else return Response.Forbidden;
    }

    public Response removeAccount(final String data) {
        UUID id = UUID.fromString(data);
        if (id.equals(this.getLoggedIn().getID())) {
            Log.error("Cannot delete current account.");
            return Response.Forbidden;
        }
        if (this.accounts.remove(id) != null) return Response.Ok;
        else return Response.NotFound;
    }

    public Response removeDiscount(final String data) {
        Stock stock = this.inventory.getStock().get(UUID.fromString(data));
        if (stock == null) return Response.NotFound;

        stock.setDiscount(null);
        return Response.Ok;
    }

    public Response removeStock(final String data) {
        if (this.inventory.getStock().remove(UUID.fromString(data)) != null) return Response.Ok;
        else return Response.NotFound;
    }

    public Response removeCustomer(final String data) {
        if (this.customers.remove(UUID.fromString(data)) != null) return Response.Ok;
        else return Response.NotFound;
    }

    public Response removeOrder(final String data) {
        if (this.accounts.remove(UUID.fromString(data)) != null) return Response.Ok;
        else return Response.NotFound;
    }

    public Response removeAutoOrder(final String data) {
        if (this.accounts.remove(UUID.fromString(data)) != null) return Response.Ok;
        else return Response.NotFound;
    }

    public Response removeNotification(final int data) {
        this.getLoggedIn().removeNotification(data);
        return Response.Ok;
    }

    public Response unlockAccount(final String data) {
        Account account = this.accounts.get(UUID.fromString(data));
        if (account == null) return Response.NotFound;
        if (account.isLocked()) {
            account.setLocked(false);
            return Response.Ok;
        } else return Response.BadRequest;
    }

    public Response updateAccount(final List<Object> data) {
        UUID id = (UUID) data.get(0);
        Account account = this.accounts.get(id);
        if (account == null) return Response.BadRequest;

        LocalDateTime birthday = (LocalDateTime) data.get(1);
        if (birthday != null) account.setBirthday(birthday);

        String name = (String) data.get(2);
        if (name != null) account.setName(name);

        String login = (String) data.get(3);
        if (login != null) account.setLogin(login);

        PermissionLevel permissions = (PermissionLevel) data.get(4);
        if (permissions != null) account.setPermissions(permissions);

        return Response.Ok;
    }

    public Response updateStock(final List<Object> data) {
        UUID id = (UUID) data.get(0);
        Stock stock = this.inventory.findStock(id);
        if (stock == null) {
            Log.error("Invalid stock ID.");
            return Response.BadRequest;
        }

        if (data.get(1) != null) {
            int quantity = (int) data.get(1);
            stock.setQuantity(quantity);
        }

        if (data.get(2) != null) {
            double price = (double) data.get(2);
            stock.setPrice(price);
        }

        String name = (String) data.get(3);
        if (name != null) stock.setName(name);

        Discount discount = (Discount) data.get(4);
        if (discount != null) stock.setDiscount(discount);

        return Response.Ok;
    }

    public Response updateDrug(final List<Object> data) {
        UUID id = (UUID) data.get(0);
        Stock stock = this.inventory.findStock(id);
        if (!(stock instanceof Drug)) {
            Log.error("Invalid item type, expected Drug, got Stock.");
            return Response.BadRequest;
        }

        Drug drug = (Drug) stock;
        if (drug == null) {
            Log.error("Invalid stock ID.");
            return Response.BadRequest;
        }

        if (data.get(1) != null) {
            int quantity = (int) data.get(1);
            drug.setQuantity(quantity);
        }

        if (data.get(2) != null) {
            double price = (double) data.get(2);
            drug.setPrice(price);
        }

        String name = (String) data.get(3);
        if (name != null) drug.setName(name);

        Discount discount = (Discount) data.get(4);
        if (discount != null) drug.setDiscount(discount);

        if (data.get(5) != null) {
            boolean is_controlled = (boolean) data.get(5);
            drug.setIsControlled(is_controlled);
        }

        String drug_name = (String) data.get(6);
        if (drug_name != null) drug.setDrugName(drug_name);

        LocalDateTime expiration_date = (LocalDateTime) data.get(7);
        if (expiration_date != null) drug.setExpirationDate(expiration_date);

        return Response.Ok;
    }

    public Response updateCustomer(final List<Object> data) {
        UUID id = (UUID) data.get(0);
        Customer customer = this.customers.get(id);
        if (customer == null) {
            Log.error("Invalid customer ID.");
            return Response.BadRequest;
        }

        LocalDateTime birthday = (LocalDateTime) data.get(1);
        if (birthday != null) customer.setBirthday(birthday);

        String name = (String) data.get(2);
        if (name != null) customer.setName(name);

        LocalDateTime last_access = (LocalDateTime) data.get(3);
        if (last_access != null) customer.setLastAccess(last_access);

        return Response.Ok;
    }

    public Response updateOrder(final List<Object> data) {
        UUID id = (UUID) data.get(0);
        Order order = this.inventory.getOrders().get(id);
        if (order == null) {
            Log.error("Invalid order ID.");
            return Response.BadRequest;
        }
        ;

        LocalDateTime shipment_date = (LocalDateTime) data.get(1);
        if (shipment_date != null) order.setShipmentDate(shipment_date);

        return Response.Ok;
    }

    /**
     * Input two arrays of the same size.
     *
     * @param barcodes
     * @param quantities
     */
    private void purchaseItems(List<UUID> barcodes, List<Integer> quantities) {
        List<Stock> order_items = new ArrayList<Stock>();

        for (int i = 0; i < barcodes.size(); i++) {
            UUID item_id = (UUID) barcodes.get(i);
            Stock stock = this.inventory.findStock(item_id);

            if (stock == null) {
                Log.error("Invalid stock ID: " + item_id);
                continue;
            }

            int purchase_quantity = (int) quantities.get(i);
            int stock_quantity = stock.getQuantity();
            if (stock_quantity < purchase_quantity) {
                Log.error("Not enough items in stock for: " + stock);
                continue;
            }

            if (stock instanceof Drug) {
                Drug drug = (Drug) stock;
                if (drug.getExpirationDate().isAfter(LocalDateTime.now())) {
                    String text = "The purchase item is expired: " + drug;
                    Log.tui(text);
                    sendNotification(new Notification(PermissionLevel.Cashier, text));
                }
            }

            stock.setQuantity(stock_quantity - purchase_quantity);
            order_items.add(stock);
        }

        if (order_items.size() == 0) {
            Log.error("No items were able to be purchased.");
        }

        this.last_purchase.clear();
        for (Stock item : order_items) {
            int purchase_quantity = item.getQuantity();
            int item_quantity = this.inventory.getStock().get(item.getID()).getQuantity();
            Log.audit("Customer purchasing new item: " + item);
            Stock inventory_item = this.inventory.getStock().get(item.getID());
            if (inventory_item == null || item_quantity < purchase_quantity) {
                Log.error("Not enough items in stock for: " + inventory_item);
                continue;
            }
            inventory_item.setQuantity(item_quantity - purchase_quantity);
            this.last_purchase.add(inventory_item);
        }
    }

    @SuppressWarnings("unchecked")
    public Response purchaseStock(final List<Object> data) {
        UUID customer_id = (UUID) data.get(0);
        Customer customer = this.customers.get(customer_id);
        if (customer == null) {
            Log.error("Invalid customer ID.");
            return Response.BadRequest;
        }

        customer.setLastAccessNow();

        List<UUID> barcodes = (List<UUID>) data.get(1);
        List<Integer> quantities = (List<Integer>) data.get(2);

        purchaseItems(barcodes, quantities);

        return Response.Ok;
    }

    public Response pickupPrescription(final List<Object> data) {
        UUID customer_id = (UUID) data.get(0);
        Customer customer = this.customers.get(customer_id);
        if (customer == null) {
            Log.error("Invalid customer ID.");
            return Response.BadRequest;
        }

        customer.setLastAccessNow();

        if (!(customer instanceof Patient)) {
            Log.error("Only patient's can have prescriptions.");
            return Response.BadRequest;
        }

        Patient patient = (Patient) customer;
        if (patient == null) {
            Log.error("Invalid customer ID.");
            return Response.BadRequest;
        }

        UUID prescription_id = (UUID) data.get(1);
        Prescription order_prescription = null;
        for (Prescription prescription : patient.getPrescriptions()) {
            if (prescription.getID().equals(prescription_id)) {
                order_prescription = prescription;
                break;
            }
        }
        if (order_prescription == null) {
            Log.error("Invalid prescription ID.");
            return Response.BadRequest;
        } else if (order_prescription != null
                && Duration.between(order_prescription.getLastFillTime(), LocalDateTime.now())
                                .compareTo(order_prescription.getRefillDuration())
                        < 0) {
            Log.error("Prescription is not ready to be filled.");
            return Response.BadRequest;
        }

        Log.audit("Filling prescription: " + order_prescription);

        List<UUID> barcodes = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        for (Stock stock : order_prescription.getItems()) {
            barcodes.add(stock.getID());
            quantities.add(stock.getQuantity());
        }

        purchaseItems(barcodes, quantities);

        order_prescription.setLastFillTime(LocalDateTime.now());

        return Response.Ok;
    }

    /**
     * @param login
     * @return
     */
    public Response checkLocked(final String login) {
        UUID key = UUID.nameUUIDFromBytes(login.getBytes());
        if (!accounts.containsKey(key)) {
            Log.error("Account does not exist.");
            return Response.NotFound;
        }
        if (getLoggedIn() != null) {
            Log.error("A user is already logged into the system.");
            return Response.BadRequest;
        }

        Account account = accounts.get(key);
        if (account.isLocked()) {
            // Do not login
            Log.info("Account is locked. It must be unlocked by an admin.");
            setLoggingIn(null);
            return Response.Forbidden;
        } else if (account.isFirstLogin()) {
            setLoggingIn(account);
            // Force relogin with newly created password
            return Response.FirstLogin;
        } else {
            setLoggingIn(account);
            return Response.GetPassword;
        }
    }

    private void sendNotification(List<Notification> new_notifications) {
        if (new_notifications == null) return;
        for (Notification notification : new_notifications) {
            sendNotification(notification);
        }
    }

    private boolean isUniqueNotification(Account account, Notification new_notification) {
        for (Notification notification : account.getNotifications()) {
            if (notification.equals(new_notification)) {
                return false;
            }
        }
        return true;
    }

    private void sendNotification(Notification new_notification) {
        if (this.getLoggedIn() == null) return;
        if (new_notification == null) return;
        for (UUID key : this.accounts.keySet()) {
            Account account = accounts.get(key);
            if (account == null) continue;
            if (account.getPermissions().compareTo(new_notification.permission()) >= 0) {
                if (!isUniqueNotification(account, new_notification)) continue;
                account.addNotification(new_notification);
                if (this.getLoggedIn().getPermissions().compareTo(new_notification.permission())
                        >= 0) {
                    Log.tui("Notification:" + new_notification.notification());
                }
            }
        }
    }

    private void updateCustomers() {
        for (UUID id : this.customers.keySet()) {
            if (this.customers.get(id).last_access.isBefore(Config.lastCustomerAccessTimeout())) {
                this.customers.remove(id);
            }
        }
    }

    private void discrepancyNotOrdered(UUID id) {
        Stock stock = this.inventory.findStock(id);
        Stock last_stock = this.inventory.getLastStock().get(id);
        Stock last_order_item = this.inventory.getLastOrderItems().get(id);

        if (stock != null && last_stock == null && last_order_item == null) {
            String text = "Stock discrepancy detected, item was not ordered: " + stock;
            Log.audit(text);
            sendNotification(new Notification(PermissionLevel.PharmacyManager, text));
        } else if (last_order_item != null) {
            this.inventory.getLastOrderItems().remove(id);
        }
    }

    private void discrepancyNotPurchased(UUID id) {
        Stock stock = this.inventory.findStock(id);
        Stock last_stock = this.inventory.getLastStock().get(id);
        int i = this.last_purchase.indexOf(id);
        Stock last_purchase = i == -1 ? null : this.last_purchase.get(i);

        if (stock == null && last_stock != null && last_purchase == null) {
            String text = "Stock discrepancy detected, item was not purchased: " + last_stock;
            Log.audit(text);
            sendNotification(new Notification(PermissionLevel.PharmacyManager, text));
            this.inventory.getLastStock().remove(id);
        } else if (last_purchase != null) {
            this.last_purchase.remove(i);
        }
    }

    public void updateDiscrepancies() {
        Set<UUID> keys = new HashSet<UUID>(this.inventory.getLastStock().keySet());
        keys.addAll(this.inventory.getStock().keySet());
        keys.addAll(this.inventory.getLastOrderItems().keySet());

        for (UUID id : keys) {
            discrepancyNotOrdered(id);
            discrepancyNotPurchased(id);
            Stock current = this.inventory.findStock(id);
            if (current != null) this.inventory.getLastStock().put(id, current);
        }
    }

    private void initAdmin() {
        Account admin = Config.initAdmin();
        admin.setFirstLogin(false);
        admin.setPassword("admin");

        this.accounts.put(admin.getLogin(), admin);
    }

    public boolean auth(PermissionLevel required) {
        Account account = getLoggedIn();

        return account != null && account.getPermissions().ordinal() >= required.ordinal();
    }

    /**
     * @param account
     */
    private void loginFailed(Account account) {
        account.setFailureAttempts(account.getFailureAttempts() + 1);
        if (account.getFailureAttempts() >= 5) {
            account.setFailureAttempts(0); // lock the account
            account.setLocked(true);
            Log.tui("Account has been locked due to many attempts.");
        } else {
            Log.tui("Failed attempts: " + account.getFailureAttempts());
        }
    }

    private void setLoggingIn(Account loggingIn) {
        this.logging_in = loggingIn;
    }

    private void setLoggedIn(Account logged_in) {
        this.logged_in = logged_in;
    }
}

class Prescription {
    // Data Members
    private UUID id;
    private List<Stock> items;
    private LocalDateTime last_fill_time;
    private Duration refill_duration;

    Prescription(List<Stock> items, Duration refill_duration) {
        this.id = UUID.randomUUID();
        this.items = items;
        this.last_fill_time = null;
        this.refill_duration = refill_duration;
        Log.audit("Prescription created: " + this);
    }

    // Getters/Setters
    public UUID getID() {
        return this.id;
    }

    public List<Stock> getItems() {
        return items;
    }

    public void setItems(final List<Stock> items) {
        this.items = items;
    }

    public LocalDateTime getLastFillTime() {
        return last_fill_time;
    }

    public void setLastFillTime(final LocalDateTime last_fill) {
        this.last_fill_time = last_fill;
    }

    public Duration getRefillDuration() {
        return refill_duration;
    }

    public void setRefillDuration(final Duration refill_duration) {
        this.refill_duration = refill_duration;
    }

    @Override
    public String toString() {
        return "[ID: "
                + this.id
                + ", Items: "
                + this.items
                + ", Last Fill Time: "
                + this.last_fill_time
                + ", Refill Duration: "
                + this.refill_duration
                + "]";
    }
}

enum CustomerType {
    Customer,
    Patient,
}

class Customer {
    protected UUID id;
    protected LocalDateTime birthday;
    protected String name;
    protected LocalDateTime last_access;

    Customer(LocalDateTime birthday, String name) {
        this.id = UUID.randomUUID();
        this.birthday = birthday;
        this.name = name;
        this.last_access = LocalDateTime.now();
        Log.audit("Customer created: " + this);
    }

    // Getters/Setters
    public UUID getID() {
        return id;
    }

    public int getAge() {
        return Period.between(this.birthday.toLocalDate(), LocalDate.now()).getYears();
    }

    public LocalDateTime getBirthday() {
        return birthday;
    }

    public void setBirthday(final LocalDateTime birthday) {
        this.birthday = birthday;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public LocalDateTime getLastAccess() {
        return last_access;
    }

    public void setLastAccess(LocalDateTime time) {
        this.last_access = time;
    }

    public void setLastAccessNow() {
        this.last_access = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "[ID: "
                + this.getID()
                + ", Birthday: "
                + this.getBirthday()
                + ", Name: "
                + this.getName()
                + ", Last access: "
                + this.getLastAccess()
                + "]";
    }
}

class Patient extends Customer {
    private List<Prescription> prescriptions;
    private List<Prescription> prescription_history;

    Patient(LocalDateTime birthday, String name) {
        super(birthday, name);
    }

    Patient(LocalDateTime birthday, String name, List<Prescription> prescriptions) {
        super(birthday, name);
        this.prescriptions = prescriptions;
        this.prescription_history = this.prescriptions;
    }

    // Getters/Setters
    public List<Prescription> getPrescriptions() {
        return prescriptions;
    }

    public void addPrescription(Prescription prescription) {
        Log.audit("Prescription added to Patient " + this.getID());
        this.prescriptions.add(prescription);
        this.prescription_history.add(prescription);
    }

    @Override
    public String toString() {
        return super.toString()
                + "-[Prescriptions: "
                + this.getPrescriptions()
                + ", History: "
                + this.prescription_history
                + "]";
    }
}

class Purchase {
    // Data Members
    private UUID id;
    private LocalDateTime purchase_date;
    private List<Stock> items;

    // Getters/Setters
    Purchase(List<Stock> items) {
        this.id = UUID.randomUUID();
        this.purchase_date = LocalDateTime.now();
        this.items = items;
        Log.audit("Purchase created: " + this);
    }

    Purchase(Stock item) {
        this.id = UUID.randomUUID();
        this.purchase_date = LocalDateTime.now();
        this.items = new ArrayList<>();
        this.items.add(item);
        Log.audit("Purchase created: " + this);
    }

    public UUID getID() {
        return id;
    }

    public LocalDateTime getPurchaseDate() {
        return purchase_date;
    }

    public void setPurchaseDate(final LocalDateTime purchase_date) {
        this.purchase_date = purchase_date;
    }

    public List<Stock> getItems() {
        return items;
    }

    public void setItems(final List<Stock> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "ID: "
                + this.id
                + ", Purchase date: "
                + this.purchase_date
                + ", Items: "
                + this.items;
    }
}

enum PermissionLevel {
    Cashier,
    PharmacyTechnician,
    Pharmacist,
    PharmacyManager,
    Admin,
}

record Notification(PermissionLevel permission, String notification) {}

class Account {
    // Data Members
    private boolean first_login;
    private boolean locked;
    private UUID id;
    private LocalDateTime birthday;
    private int failure_attempts;
    private String name;
    private UUID login;
    private UUID password;
    private List<Purchase> purchases;
    private PermissionLevel permissions;
    private List<Notification> notifications;

    // Constructors
    /**
     * @param birthday
     * @param name
     * @param login
     * @param permissions
     */
    Account(
            final LocalDateTime birthday,
            final String name,
            final String login,
            final PermissionLevel permissions) {
        this.birthday = birthday;
        this.name = name;
        this.login = UUID.nameUUIDFromBytes(login.getBytes());
        this.id = this.login;
        this.permissions = permissions;
        this.notifications = new ArrayList<Notification>();
        // Create password on first login
        this.password = null;
        this.first_login = true;
        Log.audit("Account created: " + this);
    }

    Account(
            final LocalDateTime birthday,
            final String name,
            final String login,
            final PermissionLevel permissions,
            final boolean log) {
        this.birthday = birthday;
        this.name = name;
        this.login = UUID.nameUUIDFromBytes(login.getBytes());
        this.id = this.login;
        this.permissions = permissions;
        this.notifications = new ArrayList<Notification>();
        // Create password on first login
        this.password = null;
        this.first_login = true;
        if (log) {
            Log.audit("Account created: " + this);
        }
    }

    // Getters/Setters
    public UUID getID() {
        return id;
    }

    public int getAge() {
        return Period.between(this.birthday.toLocalDate(), LocalDate.now()).getYears();
    }

    public LocalDateTime getBirthday() {
        return birthday;
    }

    public void setBirthday(final LocalDateTime birthday) {
        this.birthday = birthday;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public UUID getLogin() {
        return login;
    }

    public void setLogin(final String login) {
        this.login = UUID.nameUUIDFromBytes(login.getBytes());
        this.id = this.login;
    }

    public UUID getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = UUID.nameUUIDFromBytes(password.getBytes());
    }

    public boolean isFirstLogin() {
        return first_login;
    }

    public void setFirstLogin(final boolean first_login) {
        this.first_login = first_login;
    }

    public int getFailureAttempts() {
        return failure_attempts;
    }

    public void setFailureAttempts(final int failure_attempts) {
        this.failure_attempts = failure_attempts;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(final boolean locked) {
        this.locked = locked;
    }

    public List<Purchase> getPurchases() {
        return purchases;
    }

    public void setPurchases(final List<Purchase> purchases) {
        this.purchases = purchases;
    }

    public PermissionLevel getPermissions() {
        return permissions;
    }

    public void setPermissions(PermissionLevel permissions) {
        this.permissions = permissions;
    }

    public void addNotification(Notification notification) {
        this.notifications.add(notification);
    }

    public List<Notification> getNotifications() {
        return this.notifications;
    }

    public void printNotifications() {
        Log.tui("Notifications...");
        for (int i = 0; i < this.notifications.size(); i++) {
            Log.tui("[" + i + "]: " + this.notifications.get(i).notification());
        }
    }

    public void removeNotification(int index) {
        if (index < 0 || index >= this.notifications.size()) {
            Log.tui("Invalid notification index.");
        } else this.notifications.remove(index);
    }

    @Override
    public String toString() {
        return "[ID: "
                + this.id
                + ", Birthday: "
                + this.birthday
                + ", Name: "
                + this.name
                + ", Login: "
                + this.login
                + ", Permissions: "
                + this.permissions
                + "]";
    }
}
