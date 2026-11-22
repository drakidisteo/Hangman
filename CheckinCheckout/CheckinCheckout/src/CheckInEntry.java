import java.time.LocalDate;

public class CheckInEntry {

    public final Accommodation accommodation;

    public final String rawCheckIn;
    public final String rawCheckOut;

    public final LocalDate checkInDate;
    public final LocalDate checkOutDate;

    public final int adults;
    public final int children;
    public final boolean guestsUnknown;
    public final String rawGuests;

    public CheckInEntry(Accommodation accommodation,
                        String rawCheckIn,
                        String rawCheckOut,
                        LocalDate checkInDate,
                        LocalDate checkOutDate,
                        int adults,
                        int children,
                        boolean guestsUnknown,
                        String rawGuests) {

        this.accommodation = accommodation;
        this.rawCheckIn = rawCheckIn;
        this.rawCheckOut = rawCheckOut;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.adults = adults;
        this.children = children;
        this.guestsUnknown = guestsUnknown;
        this.rawGuests = rawGuests;
    }
}
