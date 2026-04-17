package country;

import storage.CountryStorage;
import storage.ImageAssetStore;

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
 * Repository service for creating, updating, and deleting {@link Country} records.
 *
 * <p>This class coordinates in-memory identity tracking with {@link CountryStorage} persistence
 * and {@link ImageAssetStore} image-path normalization for country data.</p>
 */
public class CountryRepository {

    private static final Set<Integer> USED_COUNTRY_IDS = new HashSet<>();
    private static final Set<String> USED_COUNTRY_NAMES = new HashSet<>();

    private final List<Country> countries = new ArrayList<>();
    private final Map<Integer, Country> countriesById = new HashMap<>();
    private final CountryStorage storage;
    private final ImageAssetStore imageAssetStore;
    private int nextId = 1;

    /**
     * Creates a repository backed by default storage components.
     */
    public CountryRepository() {
        this(new CountryStorage(), new ImageAssetStore());
    }

    /**
     * Creates a repository with explicit storage dependencies.
     *
     * @param storage country storage gateway
     * @param imageAssetStore image import/normalization helper
     */
    public CountryRepository(CountryStorage storage, ImageAssetStore imageAssetStore) {
        this.storage = storage;
        this.imageAssetStore = imageAssetStore;
    }

    /**
     * Loads countries from storage and rebuilds in-memory identity indexes.
     *
     * @throws IOException if storage read or normalization persistence fails
     */
    public void load() throws IOException {
        countries.clear();
        countriesById.clear();
        USED_COUNTRY_IDS.clear();
        USED_COUNTRY_NAMES.clear();
        countries.addAll(storage.load());
        nextId = 1;
        boolean hasImagePathUpdates = false;
        for (Country country : countries) {
            registerCountryIdentity(country);
            nextId = Math.max(nextId, country.getId() + 1);
            String normalizedImagePath = imageAssetStore.normalizeImagePath(country.getImagePath());
            if (!Objects.equals(country.getImagePath(), normalizedImagePath)) {
                country.setImagePath(normalizedImagePath);
                hasImagePathUpdates = true;
            }
            countriesById.put(country.getId(), country);
        }
        if (hasImagePathUpdates) {
            save();
        }
    }

    /**
     * Persists all current countries to storage.
     *
     * @throws IOException if writing fails
     */
    public void save() throws IOException {
        storage.save(countries);
    }

    /**
     * Returns an immutable snapshot of all countries.
     *
     * @return all known countries
     */
    public List<Country> getCountries() {
        return Collections.unmodifiableList(countries);
    }

    /**
     * Creates and registers a new country.
     *
     * @param name required country name
     * @param continent optional continent label
     * @param imageSourcePath optional source image path to import
     * @return created country
     */
    public Country addCountry(String name, String continent, String imageSourcePath) {
        String normalizedName = normalizeRequired(name, "country name");
        int candidateId = nextId;
        ensureUniqueCountryName(normalizedName);
        ensureUniqueCountryId(candidateId);

        String storedImagePath = imageAssetStore.importImage(imageSourcePath, "country", normalizedName);
        Country country = new Country(candidateId, normalizedName, normalizeOptional(continent), storedImagePath);
        registerCountryIdentity(country);
        nextId++;
        countries.add(country);
        countriesById.put(country.getId(), country);
        return country;
    }

    /**
     * Updates an existing country.
     *
     * @param countryId target country id
     * @param name required country name
     * @param continent optional continent label
     * @param imageSourcePath optional source image path to import
     * @return updated country
     */
    public Country updateCountry(int countryId, String name, String continent, String imageSourcePath) {
        Country country = countriesById.get(countryId);
        if (country == null) {
            throw new IllegalArgumentException("Country not found: id=" + countryId);
        }

        String normalizedName = normalizeRequired(name, "country name");
        String oldNameKey = normalizeRequired(country.getName(), "country name").toLowerCase();
        String newNameKey = normalizedName.toLowerCase();
        if (!oldNameKey.equals(newNameKey) && USED_COUNTRY_NAMES.contains(newNameKey)) {
            throw new IllegalArgumentException("Country already exists: " + normalizedName);
        }

        if (!oldNameKey.equals(newNameKey)) {
            USED_COUNTRY_NAMES.remove(oldNameKey);
            USED_COUNTRY_NAMES.add(newNameKey);
        }

        country.setName(normalizedName);
        country.setContinent(normalizeOptional(continent));

        if (imageSourcePath != null && !imageSourcePath.isBlank()) {
            String storedImagePath = imageAssetStore.importImage(imageSourcePath, "country", normalizedName);
            if (storedImagePath == null) {
                storedImagePath = imageAssetStore.normalizeImagePath(imageSourcePath);
            }
            country.setImagePath(storedImagePath);
        }

        return country;
    }

    /**
     * Finds a country by identifier.
     *
     * @param countryId country id
     * @return matching country, or {@code null}
     */
    public Country findById(int countryId) {
        return countriesById.get(countryId);
    }

    /**
     * Finds a country by name, case-insensitively.
     *
     * @param name country name
     * @return matching country, or {@code null}
     */
    public Country findByName(String name) {
        String normalized = normalizeOptional(name);
        if (normalized == null) {
            return null;
        }
        for (Country country : countries) {
            if (country.getName().equalsIgnoreCase(normalized)) {
                return country;
            }
        }
        return null;
    }

    /**
     * Deletes a country by identifier.
     *
     * @param countryId country id
     */
    public void deleteCountryById(int countryId) {
        Country country = countriesById.get(countryId);
        if (country == null) {
            throw new IllegalArgumentException("Country not found: id=" + countryId);
        }
        countries.remove(country);
        countriesById.remove(countryId);
        USED_COUNTRY_IDS.remove(country.getId());
        USED_COUNTRY_NAMES.remove(country.getName().trim().toLowerCase());
    }

    private void ensureUniqueCountryName(String name) {
        String normalizedNameKey = name.toLowerCase();
        if (USED_COUNTRY_NAMES.contains(normalizedNameKey)) {
            throw new IllegalArgumentException("Country already exists: " + name);
        }
    }

    private void ensureUniqueCountryId(int id) {
        if (USED_COUNTRY_IDS.contains(id)) {
            throw new IllegalArgumentException("Duplicate country id detected: " + id);
        }
    }

    private void registerCountryIdentity(Country country) {
        String nameKey = normalizeRequired(country.getName(), "country name").toLowerCase();
        if (!USED_COUNTRY_IDS.add(country.getId())) {
            throw new IllegalStateException("Duplicate country id detected: " + country.getId());
        }
        if (!USED_COUNTRY_NAMES.add(nameKey)) {
            throw new IllegalStateException("Duplicate country name detected: " + country.getName());
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
