import java.time.LocalDate;

public class CheckOutEntry {

    public final Accommodation accommodation;

    public final String rawCheckOut;
    public final LocalDate checkOutDate;

    public CheckOutEntry(Accommodation accommodation,
                         String rawCheckOut,
                         LocalDate checkOutDate) {
        this.accommodation = accommodation;
        this.rawCheckOut = rawCheckOut;
        this.checkOutDate = checkOutDate;
    }
}
