package PharmacyManagementSystem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static void tui(String message) {
        Log.tui(message);
    }

    private static void requests() {
        tui("\tInvoke a request to the backend...");
        tui("\t-1: quit");
        for (Request request : Request.values()) {
            tui("\t" + request.ordinal() + ": " + request);
        }
    }

    private static LocalDateTime expirationDate(Scanner scanner) {
        String date;
        tui("Enter year (yyyy):");
        int year = scanner.nextInt();
        scanner.nextLine();

        tui("Enter month (mm):");
        int month = scanner.nextInt();
        scanner.nextLine();

        tui("Enter day (dd):");
        int day = scanner.nextInt();
        scanner.nextLine();

        return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.MIDNIGHT);
    }

    private static Object requestData(Scanner scanner, final Request request) {
        switch (request) {
            case Login:
                return TUI.login(scanner);
            case Logout:
                return Response.Ok;
            case CreateAccount:
                return TUI.createAccount(scanner);
            case CreateStock:
                return TUI.createStock(scanner);
            case CreateDiscount:
                return TUI.createDiscountUUID(scanner);
            case CreateCustomer:
                return TUI.createCustomer(scanner);
            case CreatePrescription:
                return TUI.createPrescription(scanner);
            case CreateOrder:
                return TUI.createOrder(scanner);
            case CreateAutoOrder:
                return TUI.createAutoOrder(scanner);
            case ChangePassword:
                return TUI.changePassword(scanner);
            case GetAccounts:
                return Response.Ok;
            case GetAutoOrders:
                return Response.Ok;
            case GetInventory:
                return Response.Ok;
            case GetOrders:
                return Response.Ok;
            case GetCustomers:
                return Response.Ok;
            case GetNotifications:
                return Response.Ok;
            case RemoveAccount:
                return TUI.removeAccount(scanner);
            case RemoveDiscount:
                return TUI.removeDiscount(scanner);
            case RemoveStock:
                return TUI.removeStock(scanner);
            case RemoveCustomer:
                return TUI.removeCustomer(scanner);
            case RemoveOrder:
                return TUI.removeOrder(scanner);
            case RemoveAutoOrder:
                return TUI.removeAutoOrder(scanner);
            case RemoveNotification:
                return TUI.removeNotification(scanner);
            case UnlockAccount:
                return TUI.unlockAccount(scanner);
            case UpdateAccount:
                return TUI.updateAccount(scanner);
            case UpdateStock:
                return TUI.updateStock(scanner);
            case UpdateDrug:
                return TUI.updateDrug(scanner);
            case UpdateCustomer:
                return TUI.updateCustomer(scanner);
            case UpdateOrder:
                return TUI.updateOrder(scanner);
            case PurchaseStock:
                return TUI.purchaseStock(scanner);
            case PickupPrescription:
                return TUI.pickupPrescription(scanner);
        }

        Log.error("Invalid request data.");
        return null;
    }

    private static Object responseData(Scanner scanner, Response response) {
        String text = null;

        switch (response) {
            case FirstLogin:
                tui("First login."); // Fall through
            case NewPassword:
                tui("Create a new password:");
                text = scanner.nextLine();

                tui("Confirm your password:");
                if (text.equals(scanner.nextLine())) {
                    tui("Password updated. Please relogin now.");
                    return text;
                } else tui("Password did not match.");

                return Response.Ok;
            case GetPassword:
                tui("Enter your account password:");
                return scanner.nextLine();
            case Ok:
                tui("200: OK.");
                return Response.Ok;
            case BadRequest:
                tui("400: Bad Request.");
                return Response.BadRequest;
            case Unauthorized:
                tui("401: Unauthorized");
                return Response.Unauthorized;
            case Forbidden:
                tui("403: Forbidden");
                return Response.Forbidden;
            case NotFound:
                tui("404: Not found.");
                return Response.NotFound;
        }
        return Response.NotFound;
    }

    private static void request(Scanner scanner, API api, int input) {
        if (input > Request.values().length - 1 || input < 0) {
            throw new IllegalArgumentException("Invalid request.");
        }

        Request request = Request.values()[input];
        Object data = requestData(scanner, request);
        Log.trace("DATA: " + data);

        Response response = api.receive(request, data);
        Log.audit("Request " + request + " made with data: " + data);

        data = responseData(scanner, response);
        Log.audit("Response " + response + " received with data: " + data);

        api.send(response, data);
    }

    int input(Scanner scanner) {
        while (true) {
            try {
                requests();
                return scanner.nextInt();
            } catch (InputMismatchException e) {
                tui("Invalid request.");
                scanner.nextLine();
            } finally {
                scanner.nextLine();
            }
        }
    }

    // No time for clean csv reading :(

    public static void initAccount() {
        Backend backend = Backend.get();
        String file = "accounts.csv";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] vals = line.split(",");
                LocalDate date = LocalDate.parse(vals[0]);
                LocalDateTime time = LocalDateTime.of(date, LocalTime.now());
                String name = vals[1];
                String login = vals[2];
                PermissionLevel permissions = PermissionLevel.valueOf(vals[3]);
                boolean log = Boolean.parseBoolean(vals[4]);

                Account account = new Account(time, name, login, permissions, log);
                backend.getAccounts().put(account.getID(), account);
            }
        } catch (Exception e) {
            Log.error("Init csv reader error: " + e);
        }
    }

    public static void initStock() {
        Backend backend = Backend.get();
        String file = "stock.csv";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            List<Stock> list = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String[] vals = line.split(",");
                int quantity = Integer.parseInt(vals[0]);
                double price = Double.parseDouble(vals[1]);
                String name = vals[2];
                Stock stock = new Stock(quantity, price, name, null);
                list.add(stock);
            }
            Order order = new Order(list);
            backend.inventory.addOrder(order);
            order.setShipmentDate(LocalDateTime.now());
        } catch (Exception e) {
            Log.error("Init csv reader error: " + e);
        }
    }

    public static void initDrug() {
        Backend backend = Backend.get();
        String file = "drugs.csv";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            List<Stock> list = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String[] vals = line.split(",");
                LocalDate expirationDate = LocalDate.parse(vals[0]);
                LocalDateTime expirationDateTime =
                        LocalDateTime.of(expirationDate, LocalTime.now());
                int quantity = Integer.parseInt(vals[1]);
                double price = Double.parseDouble(vals[2]);
                String name = vals[3];
                boolean isControlled = Boolean.parseBoolean(vals[4]);
                String drugName = vals[5];

                Drug drug =
                        new Drug(
                                quantity,
                                price,
                                name,
                                null,
                                isControlled,
                                drugName,
                                expirationDateTime);

                list.add(drug);
            }
            Order order = new Order(list);
            order.setShipmentDate(LocalDateTime.now());
            backend.inventory.addOrder(order);
        } catch (Exception e) {
            Log.error("Init csv reader error: " + e);
        }
    }

    public static void initCustomers() {
        Backend backend = Backend.get();
        String file = "customers.csv";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] vals = line.split(",");
                LocalDate date = LocalDate.parse(vals[0]);
                LocalDateTime time = LocalDateTime.of(date, LocalTime.now());
                String name = vals[1];
                Customer customer = new Customer(time, name);
                backend.getCustomers().put(customer.getID(), customer);
            }
        } catch (Exception e) {
            Log.error("Init csv reader error: " + e);
        }
    }

    public static void main(String[] args) {
        Log.init();

        Backend backend = Backend.get();

        Log.audit("Initializing backend data.");
        initCustomers();
        initStock();
        initDrug();
        initAccount();
        Log.audit("Finished initializing backend.");

        API api = new API();

        tui("Welcome to the Pharmacy Management System.");

        Scanner scanner = new Scanner(System.in);

        requests();
        // Must be valid or system will shutdown.
        int request = scanner.nextInt();
        scanner.nextLine();

        while (request != -1) {
            try {
                backend.update();
                request(scanner, api, request);
            } catch (Exception e) {
                Log.error("Exception in Main: " + e.getMessage());
                e.printStackTrace();
            } finally {
                while (true) {
                    try {
                        requests();
                        request = scanner.nextInt();
                        scanner.nextLine();
                        break;
                    } catch (InputMismatchException e) {
                        tui("Invalid request.");
                        scanner.nextLine();
                    }
                }
            }
        }

        scanner.close();
        Log.clean();
    }
}
