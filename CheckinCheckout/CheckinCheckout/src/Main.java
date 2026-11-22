import java.time.LocalDate;

public class Main {
  public static void main(String[] args) {

    // Όλα από τον φάκελο Downloads
    String basePath = "C:\\Users\\Theodoros\\Downloads\\";

    // MASTER EXCEL από τα Downloads (xlsx)
    String masterPath = basePath + "MASTER EXCEL.xlsx";

    // Τα Hosthub CSV
    String checkInPath        = basePath + "Hosthub Bookings.csv";
    String checkOutPath       = basePath + "Hosthub Bookings (1).csv";
    String futureArrivalsPath = basePath + "Hosthub Bookings (2).csv";

    System.out.println("Φόρτωση αρχείων από:");
    System.out.println("MASTER:   " + masterPath);
    System.out.println("CHECKIN:  " + checkInPath);
    System.out.println("CHECKOUT: " + checkOutPath);
    System.out.println("FUTURE:   " + futureArrivalsPath);

    AccommodationManager manager = new AccommodationManager();

    // ⬇⬇ ΠΡΟΣΕΞΕ: Excel, όχι CSV ⬇⬇
    manager.loadMasterFromExcel(masterPath);

    // Αν δεν διαβάστηκε τίποτα από το MASTER, σταματάμε
    if (manager.getAccommodationCount() == 0) {
      System.out.println("### ΔΕΝ ΦΟΡΤΩΘΗΚΕ ΚΑΝΕΝΑ ΚΑΤΑΛΥΜΑ ΑΠΟ ΤΟ MASTER. ΣΤΑΜΑΤΑΩ. ###");
      return;
    }

    // Αύριο
    LocalDate targetDate = LocalDate.now().plusDays(1);

    manager.loadDailyCheckIns(checkInPath, targetDate);
    manager.loadDailyCheckOuts(checkOutPath, targetDate);
    manager.loadFutureArrivals(futureArrivalsPath);

    System.out.println("\n=== ΑΝΑΘΕΣΕΙΣ ΚΑΘΑΡΙΣΤΩΝ ===");

    manager.generateReport(targetDate);
  }
}
