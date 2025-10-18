package PharmacyManagementSystem;

import java.time.LocalDateTime;

public class Config {
    static Account initAdmin() {
        return new Account(
            LocalDateTime.of(1970, 1, 1, 0, 0),
            "Admin",
            "admin",
            PermissionLevel.Admin,
            false
        );
    }
    static LocalDateTime lastCustomerAccessTimeout() {
        return LocalDateTime.now().minusYears(5);
    }
    static LocalDateTime orderDeliveryTime() {
        return LocalDateTime.now().plusMinutes(2);
    }
    static LocalDateTime expiredNotificationTime() {
        return LocalDateTime.now().plusDays(30);
    }
    static int minDrugQuantity() {
        return 120;
    }
}
