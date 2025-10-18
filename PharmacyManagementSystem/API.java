package PharmacyManagementSystem;

import java.util.List;
enum Request {
    Login,
    Logout,
    CreateAccount,
    CreateStock,
    CreateDiscount,
    CreateCustomer,
    CreatePrescription,
    CreateOrder,
    CreateAutoOrder,
    ChangePassword,
    GetAccounts,
    GetInventory,
    GetCustomers,
    GetOrders,
    GetAutoOrders,
    GetNotifications,
    RemoveAccount,
    RemoveDiscount,
    RemoveStock,
    RemoveCustomer,
    RemoveOrder,
    RemoveAutoOrder,
    RemoveNotification,
    UnlockAccount,
    UpdateAccount,
    UpdateStock,
    UpdateDrug,
    UpdateCustomer,
    UpdateOrder,
    PurchaseStock,
    PickupPrescription,
}

enum Response {
    FirstLogin,
    GetPassword,
    NewPassword,
    Ok,
    BadRequest,
    Unauthorized,
    Forbidden,
    NotFound,
}



public class API {
    private Backend backend;

    API() {
        this.backend = Backend.get();
    }
    /**
     * @param request
     * @param data
     */
    @SuppressWarnings("unchecked")
	public Response receive(final Request request, Object data) {
        switch (request) {
            case Login:
                return backend.checkLocked((String) data);
            case Logout:
                return backend.logout();
            case CreateAccount:
                if (!backend.auth(PermissionLevel.Admin)) return Response.Forbidden;
                return backend.createAccount((Account) data);
            case CreateStock:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                backend.inventory.addStock((Stock) data);
                return Response.Ok;
            case CreateDiscount:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.createDiscount((List<Object>) data);
            case CreateCustomer:
                if (!backend.auth(PermissionLevel.Cashier)) return Response.Forbidden;
                return backend.createCustomer((Customer) data);
            case CreatePrescription:
                if (!backend.auth(PermissionLevel.Pharmacist)) return Response.Forbidden;
                return backend.createPrescription((List<Object>) data);
            case CreateOrder:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.createOrder((Order) data);
            case CreateAutoOrder:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.createAutoOrder((AutoOrder) data);
            case ChangePassword:
                if (backend.getLoggedIn() == null) return Response.Unauthorized;
                return backend.changePassword((String) data);
            case GetAccounts:
                if (!backend.auth(PermissionLevel.PharmacyTechnician)) return Response.Forbidden;

                Log.tui("Accounts: " + backend.getAccounts());
                return Response.Ok;
            case GetInventory:
                if (!backend.auth(PermissionLevel.PharmacyTechnician)) return Response.Forbidden;

                Log.tui("Inventory: " + backend.inventory.getStock());
                return Response.Ok;
            case GetCustomers:
                if (!backend.auth(PermissionLevel.PharmacyTechnician)) return Response.Forbidden;

                Log.tui("Patients: " + backend.getCustomers());
                return Response.Ok;
            case GetOrders:
                if (!backend.auth(PermissionLevel.PharmacyTechnician)) return Response.Forbidden;

                Log.tui("Orders: " + backend.inventory.getOrders());
                return Response.Ok;
            case GetAutoOrders:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;

                Log.tui("Auto Orders: " + backend.inventory.getAutoOrders());
                return Response.Ok;
            case GetNotifications:
                if (backend.getLoggedIn() == null) return Response.Forbidden;
                backend.getLoggedIn().printNotifications();
                return Response.Ok;
            case RemoveAccount:
                if (!backend.auth(PermissionLevel.Admin)) return Response.Forbidden;
                return backend.removeAccount((String) data);
            case RemoveDiscount:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.removeDiscount((String) data);
            case RemoveStock:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.removeStock((String) data);
            case RemoveCustomer:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.removeCustomer((String) data);
            case RemoveOrder:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.removeOrder((String) data);
            case RemoveAutoOrder:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.removeAutoOrder((String) data);
            case RemoveNotification:
                if (backend.getLoggedIn() == null) return Response.Unauthorized;

                return backend.removeNotification((int) data);
            case UnlockAccount:
                if (!backend.auth(PermissionLevel.Admin)) return Response.Forbidden;
                return backend.unlockAccount((String) data);
            case UpdateAccount:
                if (!backend.auth(PermissionLevel.Admin)) return Response.Forbidden;
                return backend.updateAccount((List<Object>) data);
            case UpdateStock:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.updateStock((List<Object>) data);
            case UpdateDrug:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.updateDrug((List<Object>) data);
            case UpdateCustomer:
                if (!backend.auth(PermissionLevel.Pharmacist)) return Response.Forbidden;
                return backend.updateCustomer((List<Object>) data);
            case UpdateOrder:
                if (!backend.auth(PermissionLevel.PharmacyManager)) return Response.Forbidden;
                return backend.updateOrder((List<Object>) data);
            case PurchaseStock:
                if (!backend.auth(PermissionLevel.Cashier)) return Response.Forbidden;
                return backend.purchaseStock((List<Object>) data);
            case PickupPrescription:
                if (!backend.auth(PermissionLevel.Cashier)) return Response.Forbidden;
                return backend.pickupPrescription((List<Object>) data);
        }

        return Response.NotFound;
    }

    /**
     * @param response
     * @param data
     */
    public void send(final Response response, Object data) {
        Account logging_in = backend.getLoggingIn();
        Account logged_in = backend.getLoggedIn();

        if (data == null) {
            Log.error("Null response data: " + response);
            return;
        }

        switch (response) {
            case FirstLogin:
                logging_in.setPassword((String) data);
                logging_in.setFirstLogin(false);
                break;
            case GetPassword:
                backend.login((String) data);
                break;
            case NewPassword:
                logged_in.setPassword((String) data);
                backend.logout();
        }
    }
}
