package location;

import country.Country;
import country.CountryRepository;
import storage.ImageAssetStore;
import storage.LocationStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repository service for creating, updating, and deleting {@link Location} records.
 *
 * <p>This class coordinates {@link LocationStorage} persistence, country resolution through
 * {@link CountryRepository}, and image-path handling via {@link ImageAssetStore}.</p>
 */
public class LocationRepository {

    private static final Set<Integer> USED_LOCATION_IDS = new HashSet<>();
    private static final Set<String> USED_LOCATION_NAMES = new HashSet<>();

    private final List<Location> locations = new ArrayList<>();
    private final Map<Integer, Location> locationsById = new HashMap<>();
    private final LocationStorage storage;
    private final CountryRepository countryRepository;
    private final ImageAssetStore imageAssetStore;
    private int nextId = 1;

    /**
     * Creates a repository backed by default storage components.
     *
     * @param countryRepository country lookup repository
     */
    public LocationRepository(CountryRepository countryRepository) {
        this(new LocationStorage(), countryRepository, new ImageAssetStore());
    }

    /**
     * Creates a repository with explicit storage dependencies.
     *
     * @param storage location storage gateway
     * @param countryRepository country lookup repository
     * @param imageAssetStore image import/normalization helper
     */
    public LocationRepository(LocationStorage storage, CountryRepository countryRepository, ImageAssetStore imageAssetStore) {
        this.storage = storage;
        this.countryRepository = countryRepository;
        this.imageAssetStore = imageAssetStore;
    }

    /**
     * Loads locations from storage and rebuilds in-memory identity indexes.
     *
     * @throws IOException if storage read or normalization persistence fails
     */
    public void load() throws IOException {
        locations.clear();
        locationsById.clear();
        USED_LOCATION_IDS.clear();
        USED_LOCATION_NAMES.clear();
        locations.addAll(storage.load());
        nextId = 1;
        boolean hasImagePathUpdates = false;
        for (Location location : locations) {
            registerLocationIdentity(location);
            nextId = Math.max(nextId, location.getId() + 1);
            ensureCountryReference(location);
            String normalizedImagePath = normalizeLocationImagePath(location);
            if (!Objects.equals(location.getImagePath(), normalizedImagePath)) {
                location.setImagePath(normalizedImagePath);
                hasImagePathUpdates = true;
            }
            locationsById.put(location.getId(), location);
        }
        if (hasImagePathUpdates) {
            save();
        }
    }

    /**
     * Persists all current locations to storage.
     *
     * @throws IOException if writing fails
     */
    public void save() throws IOException {
        storage.save(locations);
    }

    /**
     * Returns an immutable snapshot of all locations.
     *
     * @return all known locations
     */
    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    /**
     * Finds a location by identifier.
     *
     * @param locationId location id
     * @return matching location, or {@code null}
     */
    public Location findById(int locationId) {
        return locationsById.get(locationId);
    }

    /**
     * Finds a location by name, case-insensitively.
     *
     * @param name location name
     * @return matching location, or {@code null}
     */
    public Location findByName(String name) {
        String normalizedName = normalizeOptional(name);
        if (normalizedName == null) {
            return null;
        }
        for (Location location : locations) {
            if (location.getName().equalsIgnoreCase(normalizedName)) {
                return location;
            }
        }
        return null;
    }

    /**
     * Deletes a location by identifier.
     *
     * @param locationId location id
     */
    public void deleteLocationById(int locationId) {
        Location location = locationsById.get(locationId);
        if (location == null) {
            throw new IllegalArgumentException("Location not found: id=" + locationId);
        }
        locations.remove(location);
        locationsById.remove(locationId);
        USED_LOCATION_IDS.remove(location.getId());
        USED_LOCATION_NAMES.remove(location.getName().trim().toLowerCase());
    }

    /**
     * Creates and registers a new location.
     *
     * @param name required location name
     * @param address optional address text
     * @param city optional city text
     * @param countryId owning country id
     * @param latitude optional latitude
     * @param longitude optional longitude
     * @param imageSourcePath optional source image path to import
     * @return created location
     */
    public Location addLocation(String name, String address, String city, int countryId,
                                Double latitude, Double longitude, String imageSourcePath) {
        String normalizedName = normalizeRequired(name, "location name");
        int candidateId = nextId;
        ensureUniqueLocationName(normalizedName);
        ensureUniqueLocationId(candidateId);
        Country country = countryRepository.findById(countryId);
        if (country == null) {
            throw new IllegalArgumentException("A valid country is required for locations");
        }
        ensureLatitudeLongitudePair(latitude, longitude);
        String storedImagePath = imageAssetStore.importImage(imageSourcePath, "location", normalizedName);

        Location location = new Location(candidateId, normalizedName,
                normalizeOptional(address), normalizeOptional(city), country,
                latitude, longitude, storedImagePath);
        registerLocationIdentity(location);
        nextId++;
        locations.add(location);
        locationsById.put(location.getId(), location);
        return location;
    }

    /**
     * Updates an existing location.
     *
     * @param locationId target location id
     * @param name required location name
     * @param address optional address text
     * @param city optional city text
     * @param countryId owning country id
     * @param latitude optional latitude
     * @param longitude optional longitude
     * @param imageSourcePath optional source image path to import
     * @return updated location
     */
    public Location updateLocation(int locationId, String name, String address, String city, int countryId,
                                   Double latitude, Double longitude, String imageSourcePath) {
        Location location = locationsById.get(locationId);
        if (location == null) {
            throw new IllegalArgumentException("Location not found: id=" + locationId);
        }

        String normalizedName = normalizeRequired(name, "location name");
        String oldNameKey = normalizeRequired(location.getName(), "location name").toLowerCase();
        String newNameKey = normalizedName.toLowerCase();
        if (!oldNameKey.equals(newNameKey) && USED_LOCATION_NAMES.contains(newNameKey)) {
            throw new IllegalArgumentException("Location already exists: " + normalizedName);
        }

        Country country = countryRepository.findById(countryId);
        if (country == null) {
            throw new IllegalArgumentException("A valid country is required for locations");
        }
        ensureLatitudeLongitudePair(latitude, longitude);

        if (!oldNameKey.equals(newNameKey)) {
            USED_LOCATION_NAMES.remove(oldNameKey);
            USED_LOCATION_NAMES.add(newNameKey);
        }

        location.setName(normalizedName);
        location.setAddress(normalizeOptional(address));
        location.setCity(normalizeOptional(city));
        location.setCountry(country);
        location.setLatitude(latitude);
        location.setLongitude(longitude);

        if (imageSourcePath != null && !imageSourcePath.isBlank()) {
            String storedImagePath = imageAssetStore.importImage(imageSourcePath, "location", normalizedName);
            if (storedImagePath == null) {
                storedImagePath = imageAssetStore.normalizeImagePath(imageSourcePath);
            }
            location.setImagePath(storedImagePath);
        }

        return location;
    }

    private void ensureUniqueLocationName(String name) {
        if (USED_LOCATION_NAMES.contains(name.toLowerCase())) {
            throw new IllegalArgumentException("Location already exists: " + name);
        }
    }

    private void ensureUniqueLocationId(int id) {
        if (USED_LOCATION_IDS.contains(id)) {
            throw new IllegalArgumentException("Duplicate location id detected: " + id);
        }
    }

    private void registerLocationIdentity(Location location) {
        String nameKey = normalizeRequired(location.getName(), "location name").toLowerCase();
        if (!USED_LOCATION_IDS.add(location.getId())) {
            throw new IllegalStateException("Duplicate location id detected: " + location.getId());
        }
        if (!USED_LOCATION_NAMES.add(nameKey)) {
            throw new IllegalStateException("Duplicate location name detected: " + location.getName());
        }
    }

    private void ensureCountryReference(Location location) {
        Country country = location.getCountry();
        if (country == null) {
            Country fallback = countryRepository.findByName("Singapore");
            if (fallback != null) {
                location.setCountry(fallback);
            }
            return;
        }
        Country canonical = countryRepository.findById(country.getId());
        if (canonical == null) {
            canonical = countryRepository.findByName(country.getName());
        }
        if (canonical != null) {
            location.setCountry(canonical);
            return;
        }

        Country fallback = countryRepository.findByName("Singapore");
        if (fallback != null) {
            location.setCountry(fallback);
        }
    }

    private String normalizeLocationImagePath(Location location) {
        return imageAssetStore.normalizeImagePath(location.getImagePath());
    }

    private void ensureLatitudeLongitudePair(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude should both be filled or both left blank");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
