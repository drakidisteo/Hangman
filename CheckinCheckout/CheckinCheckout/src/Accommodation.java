public class Accommodation {

    public final String address;
    public final String cleaner;
    public final String platform;   // όνομα όπως στο master/hosthub

    public Accommodation(String address, String cleaner, String platform) {
        this.address = address != null ? address.trim() : "";
        this.cleaner = cleaner != null ? cleaner.trim() : "";
        this.platform = platform != null ? platform.trim() : "";
    }

    @Override
    public String toString() {
        return "Accommodation{" +
                "address='" + address + '\'' +
                ", cleaner='" + cleaner + '\'' +
                ", platform='" + platform + '\'' +
                '}';
    }
}
