import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class BookingsLoader {

    private final AccommodationManager manager;

    public BookingsLoader(AccommodationManager manager) {
        this.manager = manager;
    }

    // ================== ΗΜΕΡΗΣΙΕΣ ΑΦΙΞΕΙΣ ==================
    public int loadDailyCheckIns(String path,
                                 LocalDate targetDate,
                                 Map<String, List<CheckInEntry>> checkInsByCleaner) {

        int totalForTargetDate = 0;

        try (Reader in = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(in);

            for (CSVRecord rec : parser) {

                // Hosthub columns:
                // "Rental"               -> κατάλυμα
                // "Check-in"             -> άφιξη
                // "Checkout"             -> αναχώρηση
                // "Guests"               -> άτομα (Q)
                // "Guest Name / Reason"  -> όνομα / λόγος (N)
                // "Channel"              -> π.χ. Booking.com / Offline Booking (D)
                String platform   = safeGet(rec, "Rental");
                String rawIn      = safeGet(rec, "Check-in");
                String rawOut     = safeGet(rec, "Checkout");
                String rawGuests  = safeGet(rec, "Guests");
                String guestName  = safeGet(rec, "Guest Name / Reason");
                String channel    = safeGet(rec, "Channel");

                if (platform.isEmpty() || rawIn.isEmpty()) {
                    continue;
                }

                LocalDate inDate = tryParseDate(rawIn);
                if (inDate == null) {
                    continue;
                }

                // Μετράμε ΟΛΕΣ τις αφίξεις της ημέρας αυτής (για το panel)
                if (inDate.equals(targetDate)) {
                    totalForTargetDate++;
                } else {
                    // Δεν είναι η μέρα που μας ενδιαφέρει -> δεν πάει στους καθαριστές
                    continue;
                }

                LocalDate outDate = tryParseDate(rawOut);

                GuestInfo guestInfo = parseGuests(rawGuests, guestName, channel);

                Accommodation acc = manager.resolveAccommodation(platform);
                if (acc == null) {
                    // Δεν βρέθηκε στο MASTER, αλλά έχει ήδη μετρηθεί στο σύνολο
                    continue;
                }

                String cleanerKey = normalizeCleanerName(acc.cleaner);

                checkInsByCleaner
                        .computeIfAbsent(cleanerKey, k -> new ArrayList<>())
                        .add(new CheckInEntry(
                                acc,
                                rawIn,
                                rawOut,
                                inDate,
                                outDate,
                                guestInfo.adults,
                                guestInfo.children,
                                guestInfo.unknown,
                                guestInfo.raw
                        ));
            }

            System.out.println("[OK] Φορτώθηκαν αφίξεις για " + checkInsByCleaner.size() + " καθαριστές.");

        } catch (Exception e) {
            System.out.println("[ERR] Σφάλμα CheckIn: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        return totalForTargetDate;
    }

    // ================== ΗΜΕΡΗΣΙΕΣ ΑΝΑΧΩΡΗΣΕΙΣ ==================
    public int loadDailyCheckOuts(String path,
                                  LocalDate targetDate,
                                  Map<String, List<CheckOutEntry>> checkOutsByCleaner) {

        int totalForTargetDate = 0;

        try (Reader in = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(in);

            for (CSVRecord rec : parser) {

                String platform = safeGet(rec, "Rental");
                String rawOut   = safeGet(rec, "Checkout");

                if (platform.isEmpty() || rawOut.isEmpty()) {
                    continue;
                }

                LocalDate outDate = tryParseDate(rawOut);
                if (outDate == null) {
                    continue;
                }

                if (outDate.equals(targetDate)) {
                    totalForTargetDate++;
                } else {
                    continue;
                }

                Accommodation acc = manager.resolveAccommodation(platform);
                if (acc == null) {
                    continue;
                }

                String cleanerKey = normalizeCleanerName(acc.cleaner);

                checkOutsByCleaner
                        .computeIfAbsent(cleanerKey, k -> new ArrayList<>())
                        .add(new CheckOutEntry(
                                acc,
                                rawOut,
                                outDate
                        ));
            }

            System.out.println("[OK] Φορτώθηκαν αναχωρήσεις για " + checkOutsByCleaner.size() + " καθαριστές.");

        } catch (Exception e) {
            System.out.println("[ERR] Σφάλμα Checkout: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        return totalForTargetDate;
    }

    // ================== ΜΕΛΛΟΝΤΙΚΕΣ ΑΦΙΞΕΙΣ ==================
    public List<CheckInEntry> loadFutureArrivals(String path) {

        List<CheckInEntry> futureArrivals = new ArrayList<>();

        try (Reader in = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(in);

            for (CSVRecord rec : parser) {

                String platform   = safeGet(rec, "Rental");
                String rawIn      = safeGet(rec, "Check-in");
                String rawOut     = safeGet(rec, "Checkout");
                String rawGuests  = safeGet(rec, "Guests");
                String guestName  = safeGet(rec, "Guest Name / Reason");
                String channel    = safeGet(rec, "Channel");

                if (platform.isEmpty() || rawIn.isEmpty()) {
                    continue;
                }

                LocalDate inDate = tryParseDate(rawIn);
                if (inDate == null) {
                    continue;
                }

                LocalDate outDate = tryParseDate(rawOut);

                GuestInfo guestInfo = parseGuests(rawGuests, guestName, channel);

                Accommodation acc = manager.resolveAccommodation(platform);
                if (acc == null) {
                    continue;
                }

                futureArrivals.add(new CheckInEntry(
                        acc,
                        rawIn,
                        rawOut,
                        inDate,
                        outDate,
                        guestInfo.adults,
                        guestInfo.children,
                        guestInfo.unknown,
                        guestInfo.raw
                ));
            }

            System.out.println("[OK] Φορτώθηκαν μελλοντικές αφίξεις: " + futureArrivals.size());

        } catch (Exception e) {
            System.out.println("[ERR] Σφάλμα Future Arrivals: " + e.getMessage());
            e.printStackTrace(System.out);
        }

        return futureArrivals;
    }

    // ================== ΒΟΗΘΗΤΙΚΑ ==================

    private static class GuestInfo {
        final int adults;
        final int children;
        final boolean unknown;
        final String raw;

        GuestInfo(int adults, int children, boolean unknown, String raw) {
            this.adults = adults;
            this.children = children;
            this.unknown = unknown;
            this.raw = raw;
        }
    }

    private String safeGet(CSVRecord rec, String column) {
        try {
            String v = rec.get(column);
            return v == null ? "" : v.trim();
        } catch (IllegalArgumentException e) {
            // Αν δεν υπάρχει η στήλη στο CSV, γύρνα κενό αντί να σκάσει.
            return "";
        }
    }

    private String normalizeCleanerName(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * rawGuests  = στήλη "Guests" (Q)
     * guestName  = στήλη "Guest Name / Reason" (N)
     * channel    = στήλη "Channel" (D)
     *
     * Αν δεν βρούμε αριθμό ατόμων ή είναι 0:
     *   - χρησιμοποιούμε πρώτα το N (guestName)
     *   - αν είναι κενό, χρησιμοποιούμε το D (channel)
     *   - αν είναι κι αυτό κενό, χρησιμοποιούμε ό,τι έχει το rawGuests
     */
    private GuestInfo parseGuests(String rawGuests, String guestName, String channel) {
        boolean rawGuestsEmpty = (rawGuests == null || rawGuests.trim().isEmpty());
        boolean nameEmpty      = (guestName == null || guestName.trim().isEmpty());
        boolean channelEmpty   = (channel == null || channel.trim().isEmpty());

        // Δεν έχουμε καμία πληροφορία
        if (rawGuestsEmpty && nameEmpty && channelEmpty) {
            return new GuestInfo(0, 0, true, null);
        }

        if (rawGuests == null) {
            rawGuests = "";
        }

        int adults = 0;
        int children = 0;
        boolean parsed = false;

        String normalized = rawGuests.toUpperCase(Locale.ROOT).replace(" ", "");
        String[] tokens = normalized.split("/");

        for (String token : tokens) {
            if (token.matches("\\d+(Ε|E|A)")) {         // ενήλικες
                adults += Integer.parseInt(token.replaceAll("\\D+", ""));
                parsed = true;
            } else if (token.matches("\\d+(Π|P|K|C|I)")) {  // παιδιά / μωρά (P/K/C/I)
                children += Integer.parseInt(token.replaceAll("\\D+", ""));
                parsed = true;
            } else if (token.matches("\\d+")) {         // σκέτος αριθμός -> ενήλικες
                adults += Integer.parseInt(token);
                parsed = true;
            }
        }

        // Δεν βρήκαμε άτομα ή βγήκαν 0 -> guestsUnknown, και δείχνουμε λόγο
        if (!parsed || (adults + children) == 0) {
            String display;
            if (!nameEmpty) {
                display = guestName.trim();        // N κελί
            } else if (!channelEmpty) {
                display = channel.trim();          // D κελί (Offline Booking κλπ)
            } else if (!rawGuests.trim().isEmpty()) {
                display = rawGuests.trim();
            } else {
                display = null;
            }
            return new GuestInfo(0, 0, true, display);
        }

        // Κανονική περίπτωση: έχουμε άτομα
        return new GuestInfo(adults, children, false, rawGuests);
    }

    private LocalDate tryParseDate(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;

        String[] patterns = {
                "d/M/yyyy", "d/M/yy",
                "d.M.yyyy", "d.M.yy",
                "yyyy-MM-dd"
        };

        for (String p : patterns) {
            try {
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
