package storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import country.Country;
import location.Location;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence adapter for reading and writing location data as JSON.
 *
 * <p>This class is consumed by {@link location.LocationRepository} and uses custom
 * serializers/deserializers to keep location-country references stable in stored data.</p>
 */
public class LocationStorage {

    private static final String DATA_DIRECTORY = "data";
    private static final String DATA_FILE = "locations.json";

    private final Gson gson;
    private final Path dataFilePath;
    private final ImageAssetStore imageAssetStore;

    /**
     * Creates storage bound to the default location data file.
     */
    public LocationStorage() {
        this(Paths.get(DATA_DIRECTORY, DATA_FILE));
    }

    /**
     * Creates storage bound to a specific data file path.
     *
     * @param dataFilePath target JSON data path
     */
    public LocationStorage(Path dataFilePath) {
        this.dataFilePath = dataFilePath;
        this.imageAssetStore = new ImageAssetStore();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Location.class, new LocationSerializer())
                .registerTypeAdapter(Location.class, new LocationDeserializer())
                .create();
    }

    /**
     * Persists locations to the configured JSON file.
     *
     * @param locations locations to persist
     * @throws IOException if writing fails
     */
    public void save(List<Location> locations) throws IOException {
        Path directory = dataFilePath.getParent();
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        try (Writer writer = Files.newBufferedWriter(dataFilePath)) {
            gson.toJson(locations, writer);
        }
    }

    /**
     * Loads locations from the configured JSON file.
     *
     * @return loaded locations, or an empty list when no data is present
     * @throws IOException if reading fails
     */
    public List<Location> load() throws IOException {
        if (!Files.exists(dataFilePath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(dataFilePath)) {
            Type listType = new TypeToken<List<Location>>() {}.getType();
            List<Location> locations = gson.fromJson(reader, listType);
            return locations != null ? locations : new ArrayList<>();
        } catch (JsonParseException e) {
            // Recover from partially written/corrupted JSON by resetting to defaults upstream.
            return new ArrayList<>();
        }
    }

    /**
     * Serializes {@link Location} into JSON with country references.
     */
    private class LocationSerializer implements JsonSerializer<Location> {
        /**
         * Converts a location to its JSON representation.
         */
        @Override
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("name", src.getName());
            if (src.getAddress() != null) {
                obj.addProperty("address", src.getAddress());
            }
            if (src.getCity() != null) {
                obj.addProperty("city", src.getCity());
            }
            Country country = src.getCountry();
            obj.addProperty("countryId", country != null ? country.getId() : 0);
            if (src.getLatitude() != null) {
                obj.addProperty("latitude", src.getLatitude());
            }
            if (src.getLongitude() != null) {
                obj.addProperty("longitude", src.getLongitude());
            }
            if (src.getImagePath() != null) {
                obj.addProperty("imagePath", imageAssetStore.normalizeImagePath(src.getImagePath()));
            }
            return obj;
        }
    }

    /**
     * Deserializes {@link Location} from JSON with country fallback support.
     */
    private class LocationDeserializer implements JsonDeserializer<Location> {
        /**
         * Converts JSON into a location instance.
         */
        @Override
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            int id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "Unnamed";
            String address = obj.has("address") && !obj.get("address").isJsonNull() ? obj.get("address").getAsString() : null;
            String city = obj.has("city") && !obj.get("city").isJsonNull() ? obj.get("city").getAsString() : null;

            Country country;
            if (obj.has("countryId") && !obj.get("countryId").isJsonNull()) {
                int countryId = obj.get("countryId").getAsInt();
                country = new Country(countryId, "Unspecified");
            } else if (obj.has("country") && !obj.get("country").isJsonNull() && obj.get("country").isJsonObject()) {
                JsonObject countryObj = obj.getAsJsonObject("country");
                int countryId = countryObj.has("id") && !countryObj.get("id").isJsonNull()
                        ? countryObj.get("id").getAsInt() : 0;
                String countryName = countryObj.has("name") && !countryObj.get("name").isJsonNull()
                        ? countryObj.get("name").getAsString() : "Unspecified";
                country = new Country(countryId, countryName);
            } else {
                country = new Country(0, "Unspecified");
            }

            Double latitude = obj.has("latitude") && !obj.get("latitude").isJsonNull() ? obj.get("latitude").getAsDouble() : null;
            Double longitude = obj.has("longitude") && !obj.get("longitude").isJsonNull() ? obj.get("longitude").getAsDouble() : null;
            String imagePath = obj.has("imagePath") && !obj.get("imagePath").isJsonNull()
                    ? imageAssetStore.normalizeImagePath(obj.get("imagePath").getAsString()) : null;

            return new Location(id, name, address, city, country, latitude, longitude, imagePath);
        }
    }
}
