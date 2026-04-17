package location;
import country.Country;
import java.util.Objects;
import java.util.StringJoiner;

import utilities.BaseEntity;

/**
 * Domain model for a physical place used by activities.
 *
 * <p>A location references a {@link Country}, may carry geocoordinates for distance queries,
 * and is attached to {@link activity.Activity} instances throughout trip plans.</p>
 */
public class Location extends BaseEntity {

    private String address;

    private String city;

    private Country country;

    private Double latitude;

    private Double longitude;

    private String imagePath;

    /**
     * Creates a location with only required identity fields.
     *
     * @param id location identifier
     * @param name location display name
     */
    public Location(int id, String name) {
        super(id, name);
    }

    /**
     * Creates a location without an image path.
     *
     * @param id location identifier
     * @param name location display name
     * @param address address text, or {@code null}
     * @param city city text, or {@code null}
     * @param country owning country
     * @param latitude latitude, or {@code null}
     * @param longitude longitude, or {@code null}
     */
    public Location(int id, String name, String address, String city, Country country, Double latitude, Double longitude) {
        this(id, name, address, city, country, latitude, longitude, null);
    }

    /**
     * Creates a location with all persisted fields.
     *
     * @param id location identifier
     * @param name location display name
     * @param address address text, or {@code null}
     * @param city city text, or {@code null}
     * @param country owning country
     * @param latitude latitude, or {@code null}
     * @param longitude longitude, or {@code null}
     * @param imagePath image path, or {@code null}
     */
    public Location(int id, String name, String address, String city, Country country,
                    Double latitude, Double longitude, String imagePath) {
        super(id, name);
        this.address = address;
        this.city = city;
        setCountry(country);
        this.latitude = latitude;
        this.longitude = longitude;
        this.imagePath = imagePath;
    }

    /**
     * Returns the optional address text.
     *
     * @return address text, or {@code null}
     */
    public String getAddress() {
        return address;
    }

    /**
     * Updates the optional address text.
     *
     * @param address address text, or {@code null}
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Returns the optional city text.
     *
     * @return city text, or {@code null}
     */
    public String getCity() {
        return city;
    }

    /**
     * Updates the optional city text.
     *
     * @param city city text, or {@code null}
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * Returns the owning country.
     *
     * @return owning country
     */
    public Country getCountry() {
        return country;
    }

    /**
     * Updates the owning country.
     *
     * @param country owning country
     */
    public void setCountry(Country country) {
        this.country = Objects.requireNonNull(country, "country");
    }

    /**
     * Returns the optional latitude.
     *
     * @return latitude, or {@code null}
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Updates the optional latitude.
     *
     * @param latitude latitude, or {@code null}
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /**
     * Returns the optional longitude.
     *
     * @return longitude, or {@code null}
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Updates the optional longitude.
     *
     * @param longitude longitude, or {@code null}
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
     * Returns the normalized image path.
     *
     * @return image path, or {@code null}
     */
    public String getImagePath() {
        return imagePath;
    }

    /**
     * Updates the normalized image path.
     *
     * @param imagePath image path, or {@code null}
     */
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    /**
     * Computes the distance to another location using the Haversine formula.
     *
     * @param other other location
     * @return distance in kilometers
     * @throws IllegalStateException if either location lacks latitude/longitude
     */
    public double distanceTo(Location other) {
        Objects.requireNonNull(other, "other");
        if (this.latitude == null || this.longitude == null
                || other.latitude == null || other.longitude == null) {
            throw new IllegalStateException("Both locations must have latitude and longitude for distance calculation");
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double startLatRad = Math.toRadians(this.latitude);
        double otherLatRad = Math.toRadians(other.latitude);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(startLatRad) * Math.cos(otherLatRad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        StringJoiner locationBits = new StringJoiner(", ");
        if (city != null && !city.isBlank()) {
            locationBits.add(city);
        }
        if (country != null && country.getName() != null && !country.getName().isBlank()) {
            locationBits.add(country.getName());
        }

        String primary = (getName() != null && !getName().isBlank()) ? getName() : "Location";
        String region = locationBits.length() > 0 ? " (" + locationBits + ")" : "";
        String addressPart = (address != null && !address.isBlank()) ? " | " + address : "";
        return primary + region + addressPart;
    }

}
