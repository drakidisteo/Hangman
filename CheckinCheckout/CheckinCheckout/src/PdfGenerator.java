import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Απλή υλοποίηση: γράφει κείμενο σε αρχείο .pdf (χωρίς PDFBox).
 * Αν θέλεις αργότερα κανονικό PDF, αλλάζεις ΜΟΝΟ αυτή την κλάση.
 */
public class PdfGenerator {

    public static void generate(String outputPath, String textContent) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            bw.write(textContent);
        }
    }
}
