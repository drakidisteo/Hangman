import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

public class AccommodationManager {

    private static final String MEMORY_FILE = "name_map.txt";

    // MASTER accommodations
    final List<Accommodation> accommodations = new ArrayList<>();

    // normalized(platformName) -> Accommodation
    final Map<String, Accommodation> platformMap = new HashMap<>();

    // check-ins / outs grouped by cleaner for target date
    final Map<String, List<CheckInEntry>> checkInsByCleaner = new HashMap<>();
    final Map<String, List<CheckOutEntry>> checkOutsByCleaner = new HashMap<>();

    // future arrivals
    final List<CheckInEntry> futureArrivals = new ArrayList<>();

    // counters
    int totalCheckInsCsvForTargetDate = 0;
    int totalCheckOutsCsvForTargetDate = 0;

    // memory for fuzzy mappings
    private final Map<String, String> fuzzyMatchCache = new HashMap<>();

    private final Scanner scanner = new Scanner(System.in);

    private final BookingsLoader bookingsLoader;
    private final CleanerReportGenerator reportGenerator;

    public AccommodationManager() {
        loadMemory();
        this.bookingsLoader = new BookingsLoader(this);
        this.reportGenerator = new CleanerReportGenerator();
    }

    // =====================================================
    // LOAD MASTER
    // =====================================================

    public void loadMasterFromExcel(String path) {
        accommodations.clear();
        platformMap.clear();

        try (FileInputStream fis = new FileInputStream(path);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            boolean first = true;

            for (Row row : sheet) {
                if (first) { first = false; continue; }
                if (row == null) continue;

                String address  = getCellString(row, 0);   // A
                String cleaner  = getCellString(row, 4);   // E
                String platform = getCellString(row, 7);   // H

                if (address.isEmpty() && platform.isEmpty()) continue;

                Accommodation acc = new Accommodation(address, cleaner, platform);
                accommodations.add(acc);

                String key = normalizeKey(platform);
                if (!key.isEmpty()) platformMap.put(key, acc);
            }

            System.out.println("[OK] Φορτώθηκαν " + accommodations.size() + " καταλύματα από MASTER Excel.");

        } catch (Exception e) {
            System.out.println("[ERR] Σφάλμα MASTER: " + e.getMessage());
        }
    }

    // =====================================================
    // PUBLIC API (Main calls)
    // =====================================================

    public int getAccommodationCount() {
        return accommodations.size();
    }

    public void loadDailyCheckIns(String path, LocalDate targetDate) {
        checkInsByCleaner.clear();
        totalCheckInsCsvForTargetDate =
                bookingsLoader.loadDailyCheckIns(path, targetDate, checkInsByCleaner);
    }

    public void loadDailyCheckOuts(String path, LocalDate targetDate) {
        checkOutsByCleaner.clear();
        totalCheckOutsCsvForTargetDate =
                bookingsLoader.loadDailyCheckOuts(path, targetDate, checkOutsByCleaner);
    }

    public void loadFutureArrivals(String path) {
        futureArrivals.clear();
        futureArrivals.addAll(bookingsLoader.loadFutureArrivals(path));
    }

    public void generateReport(LocalDate targetDate) {
        reportGenerator.generateReport(
                targetDate,
                checkInsByCleaner,
                checkOutsByCleaner,
                futureArrivals,
                totalCheckInsCsvForTargetDate,
                totalCheckOutsCsvForTargetDate
        );
    }

    // =====================================================
    // RESOLVE ACCOMMODATION (FULL REWRITE)
    // =====================================================

    public Accommodation resolveAccommodation(String hosthubPlatformOriginal) {

        if (hosthubPlatformOriginal == null || hosthubPlatformOriginal.trim().isEmpty()) {
            System.out.println("⚠ Hosthub name is empty. Δεν επιτρέπεται.");
            return askForManualEntry("(empty)");
        }

        String original = hosthubPlatformOriginal.trim();

        // 1) Exact match
        Accommodation acc = platformMap.get(normalizeKey(original));
        if (acc != null) return acc;

        // 2) Memory match
        String mapped = fuzzyMatchCache.get(original);
        if (mapped != null && !mapped.isBlank()) {
            acc = platformMap.get(normalizeKey(mapped));
            if (acc != null) return acc;
        }

        // 3) Fuzzy match (always propose best)
        double bestScore = -1;
        Accommodation bestAcc = null;

        for (Accommodation candidate : accommodations) {
            if (candidate.platform == null || candidate.platform.isBlank()) continue;

            double score = similarityScore(original, candidate.platform);
            if (score > bestScore) {
                bestScore = score;
                bestAcc = candidate;
            }
        }

        // Score = 1.0 -> PERFECT match (auto accept)
        final double PERFECT = 1.0;

        if (bestAcc != null) {

            if (Math.abs(bestScore - PERFECT) < 0.000001) {
                // Perfect match => always accept
                fuzzyMatchCache.put(original, bestAcc.platform);
                appendMemory(original, bestAcc.platform);
                return bestAcc;
            }

            // Otherwise => ask
            System.out.println("\n✦ Hosthub όνομα: \"" + original + "\"");
            System.out.println("   Προτεινόμενο: \"" + bestAcc.platform +
                    "\" (score=" + String.format(Locale.ROOT, "%.2f", bestScore) + ")");
            System.out.print("   Είναι το ίδιο; (Y/N): ");

            String ans = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
            if (ans.equals("y") || ans.equals("yes") || ans.equals("ν") || ans.equals("ναι")) {
                fuzzyMatchCache.put(original, bestAcc.platform);
                appendMemory(original, bestAcc.platform);
                return bestAcc;
            }
        }

        // 4) STRICT manual entry (no skip allowed)
        return askForManualEntry(original);
    }

    private Accommodation askForManualEntry(String original) {
        while (true) {
            System.out.println("\n❗ Δεν βρέθηκε καταχώρηση για:");
            System.out.println("   \"" + original + "\"");
            System.out.println("   Πληκτρολόγησε ΑΚΡΙΒΩΣ όπως είναι στο MASTER Excel:");
            System.out.print("   Master name: ");

            String manual = scanner.nextLine().trim();

            if (manual.isEmpty()) {
                System.out.println("⚠ Δεν επιτρέπεται κενό. Ξαναπροσπάθησε.");
                continue;
            }

            Accommodation acc = platformMap.get(normalizeKey(manual));
            if (acc == null) {
                System.out.println("⚠ \"" + manual + "\" δεν υπάρχει στο MASTER. Ξαναπροσπάθησε.");
                continue;
            }

            fuzzyMatchCache.put(original, acc.platform);
            appendMemory(original, acc.platform);
            return acc;
        }
    }

    // =====================================================
    // Excel helpers
    // =====================================================

    private String getCellString(Row row, int index) {
        if (row == null) return "";
        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date d = cell.getDateCellValue();
                    LocalDate ld = d.toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                    yield ld.toString();
                } else {
                    yield String.valueOf(cell.getNumericCellValue()).trim();
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue()).trim();
            default -> "";
        };
    }

    // =====================================================
    // NORMALIZATION / SIMILARITY
    // =====================================================

    private String normalizeKey(String s) {
        return hardNormalize(s);
    }

    private String hardNormalize(String s) {
        if (s == null) return "";

        s = s.replace('\u00A0',' ')
                .replace('\u2007',' ')
                .replace('\u202F',' ');

        s = s.trim().toLowerCase(Locale.ROOT);

        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        s = s.replace('Μ','m').replace('μ','m')
                .replace('Ρ','p').replace('ρ','p');

        s = s.replace('–','-').replace('—','-');
        s = s.replaceAll("\\s+by\\s+mps\\b", " ");
        s = s.replaceAll("\\b\\d{6,}\\b", " ");
        s = s.replaceAll("[-_,]+", " ");
        s = s.replaceAll("\\s+"," ").trim();

        return s;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) dp[i][0]=i;
        for (int j=0;j<=b.length();j++) dp[0][j]=j;

        for (int i=1;i<=a.length();i++) {
            for (int j=1;j<=b.length();j++) {
                int cost = (a.charAt(i-1)==b.charAt(j-1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i-1][j]+1, dp[i][j-1]+1),
                        dp[i-1][j-1]+cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private double similarityScore(String a, String b) {
        String na = hardNormalize(a);
        String nb = hardNormalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0.0;

        Set<String> ta = new HashSet<>(Arrays.asList(na.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(nb.split(" ")));

        Set<String> inter = new HashSet<>(ta); inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta); union.addAll(tb);

        double jaccard = union.isEmpty()?0.0:(double)inter.size()/union.size();
        double levSim = 1.0 - (double)levenshtein(na,nb)/Math.max(na.length(),nb.length());

        return 0.7*jaccard + 0.3*levSim;
    }

    // =====================================================
    // MEMORY
    // =====================================================

    private void loadMemory() {
        File f = new File(MEMORY_FILE);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\t",2);
                if (p.length == 2) fuzzyMatchCache.put(p[0], p[1]);
            }

        } catch (Exception e) {
            System.out.println("[WARN] Σφάλμα μνήμης: " + e.getMessage());
        }
    }

    protected void appendMemory(String hosthubName, String masterName) {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(MEMORY_FILE,true), StandardCharsets.UTF_8))) {

            bw.write(hosthubName + "\t" + masterName);
            bw.newLine();

        } catch (Exception e) {
            System.out.println("[WARN] Σφάλμα εγγραφής μνήμης: " + e.getMessage());
        }
    }
}
