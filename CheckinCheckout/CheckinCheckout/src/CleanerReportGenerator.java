import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CleanerReportGenerator {

    public void generateReport(LocalDate targetDate,
                               Map<String, List<CheckInEntry>> checkInsByCleaner,
                               Map<String, List<CheckOutEntry>> checkOutsByCleaner,
                               List<CheckInEntry> futureArrivals,
                               int totalCheckInsCsvForTargetDate,
                               int totalCheckOutsCsvForTargetDate) {

        DateTimeFormatter folderFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String dateStr = targetDate.format(folderFmt);

        // Φάκελος επιφάνειας εργασίας
        String userHome = System.getProperty("user.home");
        File desktopFolder = new File(userHome + File.separator + "Desktop");

        // 🧹 Σβήσε ΟΛΟΥΣ τους παλιούς φακέλους "καθαριστές "
        File[] desktopFiles = desktopFolder.listFiles();
        if (desktopFiles != null) {
            for (File f : desktopFiles) {
                if (f.isDirectory() && f.getName().startsWith("καθαριστές ")) {
                    System.out.println("Διαγραφή παλιού φακέλου καθαριστών: " + f.getAbsolutePath());
                    deleteFolder(f);
                }
            }
        }

        // Δημιουργία νέου φακέλου για τη συγκεκριμένη ημερομηνία
        File folder = new File(desktopFolder, "καθαριστές " + dateStr);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        System.out.println("Φάκελος δημιουργίας αρχείων καθαριστών: " + folder.getAbsolutePath());
        System.out.println();

        // Συγκέντρωση & ταξινόμηση ονομάτων καθαριστών
        Set<String> cleanerNames = new HashSet<>();
        cleanerNames.addAll(checkInsByCleaner.keySet());
        cleanerNames.addAll(checkOutsByCleaner.keySet());

        List<String> cleaners = new ArrayList<>(cleanerNames);
        cleaners.sort(Comparator.nullsLast(String::compareToIgnoreCase));

        for (String cleaner : cleaners) {
            if (cleaner == null || cleaner.isBlank()) continue;

            List<CheckInEntry> ciList = new ArrayList<>(checkInsByCleaner.getOrDefault(cleaner, Collections.emptyList()));
            List<CheckOutEntry> coList = new ArrayList<>(checkOutsByCleaner.getOrDefault(cleaner, Collections.emptyList()));

            // Αν δεν έχει τίποτα, δεν δημιουργούμε αρχείο
            if (ciList.isEmpty() && coList.isEmpty()) continue;

            // Ταξινόμηση για πιο ωραία σειρά
            ciList.sort(Comparator.comparing(e -> e.accommodation.address));
            coList.sort(Comparator.comparing(e -> e.accommodation.address));

            // Φτιάχνουμε:
            // - ζευγάρια Αναχώρηση-Άφιξη
            // - μόνο Αφίξεις
            // - μόνο Αναχωρήσεις
            List<PairCheckInOut> inAndOut = new ArrayList<>();
            List<CheckInEntry> onlyIn = new ArrayList<>(ciList);
            List<CheckOutEntry> onlyOut = new ArrayList<>(coList);

            Map<String, List<CheckInEntry>> ciByAddress = new HashMap<>();
            for (CheckInEntry ci : ciList) {
                String addr = normalizeAddress(ci.accommodation.address);
                ciByAddress.computeIfAbsent(addr, k -> new ArrayList<>()).add(ci);
            }

            for (CheckOutEntry co : coList) {
                String addr = normalizeAddress(co.accommodation.address);
                List<CheckInEntry> possibleArrivals = ciByAddress.getOrDefault(addr, Collections.emptyList());

                CheckInEntry matchedArrival = null;
                for (CheckInEntry ci : possibleArrivals) {
                    if (onlyIn.contains(ci)) {
                        matchedArrival = ci;
                        break;
                    }
                }

                if (matchedArrival != null) {
                    inAndOut.add(new PairCheckInOut(co, matchedArrival));
                    onlyIn.remove(matchedArrival);
                    onlyOut.remove(co);
                }
            }

            // Δημιουργία TXT αρχείου ανά καθαριστή
            String cleanerFileName = cleaner.replaceAll("[\\\\/:*?\"<>|]", "_");
            File txtFile = new File(folder, cleanerFileName + ".txt");

            try (BufferedWriter txt = new BufferedWriter(new FileWriter(txtFile, false))) {

                // Header: Καλησπέρα + αυριανή μέρα + ημερομηνία
                String dow = getGreekDayOfWeek(targetDate.getDayOfWeek());
                writeBoth(txt, "Καλησπέρα σας, αύριο " + dow + ", " + formatDate(targetDate) + "\n\n");

                // --- ΑΝΑΧΩΡΗΣΕΙΣ-ΑΦΙΞΕΙΣ ---
                if (!inAndOut.isEmpty()) {
                    writeBoth(txt, "ΑΝΑΧΩΡΗΣΕΙΣ-ΑΦΙΞΕΙΣ:\n");
                    int idx = 1;
                    for (PairCheckInOut pair : inAndOut) {
                        writeDepartureArrivalTxt(idx, pair.checkout, pair.checkin, txt, futureArrivals);
                        idx++;
                    }
                    writeBoth(txt, "\n");
                }

                // --- ΑΦΙΞΕΙΣ ---
                if (!onlyIn.isEmpty()) {
                    writeBoth(txt, "ΑΦΙΞΕΙΣ:\n");
                    int idx = 1;
                    for (CheckInEntry ci : onlyIn) {
                        writeOnlyArrivalTxt(idx, ci, txt, futureArrivals);
                        idx++;
                    }
                    writeBoth(txt, "\n");
                }

                // --- ΑΝΑΧΩΡΗΣΕΙΣ ---
                if (!onlyOut.isEmpty()) {
                    writeBoth(txt, "ΑΝΑΧΩΡΗΣΕΙΣ:\n");
                    int idx = 1;
                    for (CheckOutEntry co : onlyOut) {
                        writeOnlyDepartureTxt(idx, co, txt, futureArrivals);
                        idx++;
                    }
                    writeBoth(txt, "\n");
                }

                System.out.println("[OK] TXT δημιουργήθηκε: " + txtFile.getAbsolutePath());

            } catch (Exception e) {
                System.out.println("[ERR] Σφάλμα για καθαριστή " + cleaner + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("==============================");
        System.out.println("ΣΥΝΟΛΟ ΑΦΙΞΕΩΝ: " + sumCheckIns(checkInsByCleaner));
        System.out.println("ΣΥΝΟΛΟ ΑΝΑΧΩΡΗΣΕΩΝ: " + sumCheckOuts(checkOutsByCleaner));
        System.out.println();
        System.out.println("ΑΦΙΞΕΙΣ στο CSV: " + totalCheckInsCsvForTargetDate);
        System.out.println("ΑΝΑΧΩΡΗΣΕΙΣ στο CSV: " + totalCheckOutsCsvForTargetDate);
        System.out.println();
        System.out.println("Ημερομηνία στόχος: " + targetDate +
                " (" + getGreekDayOfWeek(targetDate.getDayOfWeek()) + ")");
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";
        return address.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private int sumCheckIns(Map<String, List<CheckInEntry>> map) {
        return map.values().stream().mapToInt(List::size).sum();
    }

    private int sumCheckOuts(Map<String, List<CheckOutEntry>> map) {
        return map.values().stream().mapToInt(List::size).sum();
    }

    private String getGreekDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Δευτέρα";
            case TUESDAY -> "Τρίτη";
            case WEDNESDAY -> "Τετάρτη";
            case THURSDAY -> "Πέμπτη";
            case FRIDAY -> "Παρασκευή";
            case SATURDAY -> "Σάββατο";
            case SUNDAY -> "Κυριακή";
        };
    }

    private String buildGuestText(CheckInEntry ci) {

        // Άγνωστοι επισκέπτες
        if (ci.guestsUnknown) {
            if (ci.rawGuests != null && !ci.rawGuests.isBlank()) {
                return "Άγνωστος αριθμός επισκεπτών (" + ci.rawGuests + ")";
            } else {
                return "Άγνωστος αριθμός επισκεπτών";
            }
        }

        int adults = ci.adults;
        int children = ci.children;

        // Καμία πληροφορία
        if (adults + children == 0) {
            return "0 επισκέπτες";
        }

        // ΜΟΝΟ ενήλικες
        if (children == 0) {
            if (adults == 1) {
                return "1 επισκέπτης";
            } else {
                return adults + " επισκέπτες";
            }
        }

        // ΜΟΝΟ παιδιά
        if (adults == 0) {
            if (children == 1) {
                return "1 παιδί";
            } else {
                return children + " παιδιά";
            }
        }

        // Ενήλικες + παιδιά
        String a = adults == 1 ? "1 ενήλικας" : adults + " ενήλικες";
        String c = children == 1 ? "1 παιδί" : children + " παιδιά";

        return a + " + " + c;
    }

    private String formatAddress(String address) {
        if (address == null) return "";
        String cleaned = address.replaceAll("\\s+", " ").trim();
        // Αφαίρεση τελικού "#123" (ή παρόμοιου)
        cleaned = cleaned.replaceAll("\\s*#\\d+\\s*$", "");
        return cleaned.trim();
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "-";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(fmt);
    }

    private CheckInEntry findNextArrivalForAccommodation(Accommodation acc,
                                                         LocalDate fromDate,
                                                         List<CheckInEntry> futureArrivals) {

        if (futureArrivals == null) return null;

        CheckInEntry best = null;
        for (CheckInEntry ci : futureArrivals) {
            if (ci.accommodation == null) continue;
            if (!normalizeAddress(ci.accommodation.address)
                    .equalsIgnoreCase(normalizeAddress(acc.address))) {
                continue;
            }
            if (ci.checkInDate == null) continue;
            if (ci.checkInDate.isBefore(fromDate)) continue;

            if (best == null || ci.checkInDate.isBefore(best.checkInDate)) {
                best = ci;
            }
        }
        return best;
    }

    private static class PairCheckInOut {
        final CheckOutEntry checkout;
        final CheckInEntry checkin;

        PairCheckInOut(CheckOutEntry checkout, CheckInEntry checkin) {
            this.checkout = checkout;
            this.checkin = checkin;
        }
    }

    // -------- Helpers για εγγραφή TXT + IntelliJ --------

    // Γράφει ΚΑΙ στο αρχείο ΚΑΙ στο System.out
    private void writeBoth(BufferedWriter txt, String text) throws Exception {
        txt.write(text);
        System.out.print(text);
    }

    private void writeDepartureArrivalTxt(int index,
                                          CheckOutEntry co,
                                          CheckInEntry ci,
                                          BufferedWriter txt,
                                          List<CheckInEntry> futureArrivals) throws Exception {

        String indent4 = "    ";
        String indent8 = indent4 + indent4;

        // Γραμμή διεύθυνσης με αρίθμηση (4 κενά)
        writeBoth(txt, indent4 + index + ". " + formatAddress(co.accommodation.address) + "\n");

        // Αναχώρηση
        writeBoth(txt, indent4 + "Αναχώρηση: " + formatDate(co.checkOutDate) + "\n");

        // Άφιξη
        writeBoth(txt, indent4 + "Άφιξη:\n");
        writeBoth(txt, indent8 + "Διάστημα: " + formatDate(ci.checkInDate) +
                " - " + formatDate(ci.checkOutDate) + "\n");
        writeBoth(txt, indent8 + buildGuestText(ci) + "\n");

        // Επόμενη άφιξη
        CheckInEntry next = findNextArrivalForAccommodation(ci.accommodation, ci.checkOutDate, futureArrivals);
        writeBoth(txt, indent4 + "Επόμενη άφιξη:\n");
        if (next != null) {
            writeBoth(txt, indent8 + "Διάστημα: " + formatDate(next.checkInDate) +
                    " - " + formatDate(next.checkOutDate) + "\n");
            writeBoth(txt, indent8 + buildGuestText(next) + "\n");
        } else {
            writeBoth(txt, indent8 + "(δεν υπάρχει επόμενη άφιξη)\n");
        }

        writeBoth(txt, "\n");
    }

    private void writeOnlyArrivalTxt(int index,
                                     CheckInEntry ci,
                                     BufferedWriter txt,
                                     List<CheckInEntry> futureArrivals) throws Exception {

        String indent4 = "    ";
        String indent8 = indent4 + indent4;

        // Γραμμή διεύθυνσης με αρίθμηση (4 κενά)
        writeBoth(txt, indent4 + index + ". " + formatAddress(ci.accommodation.address) + "\n");

        // Άφιξη
        writeBoth(txt, indent4 + "Άφιξη:\n");
        writeBoth(txt, indent8 + "Διάστημα: " + formatDate(ci.checkInDate) +
                " - " + formatDate(ci.checkOutDate) + "\n");
        writeBoth(txt, indent8 + buildGuestText(ci) + "\n");

        // Επόμενη άφιξη
        CheckInEntry next = findNextArrivalForAccommodation(ci.accommodation, ci.checkOutDate, futureArrivals);
        writeBoth(txt, indent4 + "Επόμενη άφιξη:\n");
        if (next != null) {
            writeBoth(txt, indent8 + "Διάστημα: " + formatDate(next.checkInDate) +
                    " - " + formatDate(next.checkOutDate) + "\n");
            writeBoth(txt, indent8 + buildGuestText(next) + "\n");
        } else {
            writeBoth(txt, indent8 + "(δεν υπάρχει επόμενη άφιξη)\n");
        }

        writeBoth(txt, "\n");
    }

    private void writeOnlyDepartureTxt(int index,
                                       CheckOutEntry co,
                                       BufferedWriter txt,
                                       List<CheckInEntry> futureArrivals) throws Exception {

        String indent4 = "    ";
        String indent8 = indent4 + indent4;

        // Γραμμή διεύθυνσης με αρίθμηση (4 κενά)
        writeBoth(txt, indent4 + index + ". " + formatAddress(co.accommodation.address) + "\n");

        // Αναχώρηση
        writeBoth(txt, indent4 + "Αναχώρηση: " + formatDate(co.checkOutDate) + "\n");

        // Επόμενη άφιξη
        CheckInEntry next = findNextArrivalForAccommodation(co.accommodation, co.checkOutDate, futureArrivals);
        writeBoth(txt, indent4 + "Επόμενη άφιξη:\n");
        if (next != null) {
            writeBoth(txt, indent8 + "Διάστημα: " + formatDate(next.checkInDate) +
                    " - " + formatDate(next.checkOutDate) + "\n");
            writeBoth(txt, indent8 + buildGuestText(next) + "\n");
        } else {
            writeBoth(txt, indent8 + "(δεν υπάρχει επόμενη άφιξη)\n");
        }

        writeBoth(txt, "\n");
    }

    // --------- Helper για διαγραφή φακέλων ---------

    private void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) return;

        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    if (!f.delete()) {
                        System.out.println("⚠ Δεν μπόρεσα να διαγράψω το αρχείο: " + f.getAbsolutePath());
                    }
                }
            }
        }
        if (!folder.delete()) {
            System.out.println("⚠ Δεν μπόρεσα να διαγράψω τον φάκελο: " + folder.getAbsolutePath());
        }
    }
}
