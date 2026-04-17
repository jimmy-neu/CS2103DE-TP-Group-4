package storage;

import activity.Activity;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import country.Country;
import expense.Expense;
import location.Location;
import trip.Trip;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistence gateway for saving and loading trip aggregates as JSON.
 *
 * <p>This class is used by {@link trip.TripManager} and coordinates Gson adapters for
 * {@link Trip}, {@link Activity}, {@link LocalDateTime}, and related reference serialization.</p>
 */
public class JsonStorage {

    /** The folder where we save the JSON file */
    private static final String DATA_DIRECTORY = "data";

    /** The filename for our saved trips */
    private static final String DATA_FILE = "trips.json";

    /** The Gson instance configured with our custom settings */
    private final Gson gson;

    /** The full path to the data file (e.g., "data/trips.json") */
    private final Path dataFilePath;

    private static final ImageAssetStore IMAGE_ASSET_STORE = new ImageAssetStore();

    /**
     * Creates a new JsonStorage with default settings.
     * The data file will be at "data/trips.json" relative to where the app runs.
     */
    public JsonStorage() {
        this(Paths.get(DATA_DIRECTORY, DATA_FILE));
    }

    /**
     * Creates a new JsonStorage that saves/loads from a specific file path.
     * This constructor is useful for testing with a different file location.
     */
    public JsonStorage(Path dataFilePath) {
        this.dataFilePath = dataFilePath;
        this.gson = createGson();
    }

    /**
     * Configures Gson with all the custom settings we need.
     *
     * Think of this as "teaching" Gson how to handle our specific classes.
     * Out of the box, Gson knows how to handle simple types like String,
     * int, float, etc. But it needs help with:
     *   - LocalDateTime (our custom adapter tells it to use ISO format strings)
     *   - BufferedImage (we tell it to skip these entirely)
     *   - Trip and Activity (we tell it how to reconstruct them properly)
     */
    private Gson createGson() {
        return new GsonBuilder()
                // "Pretty printing" means the JSON file will have nice indentation
                // and line breaks, making it human-readable (great for debugging!)
                .setPrettyPrinting()

                // Register our custom LocalDateTime adapter so Gson knows
                // how to convert dates like 2026-04-03T09:00:00
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())

                // Register custom deserializers for Trip and Activity.
                // These are needed because Trip and Activity constructors
                // validate their inputs (e.g., start must be before end).
                // Gson's default approach of setting fields directly would
                // bypass these checks, so we manually call the constructors.
                .registerTypeAdapter(Trip.class, new TripSerializer())
                .registerTypeAdapter(Trip.class, new TripDeserializer())
                .registerTypeAdapter(Activity.class, new ActivitySerializer())
                .registerTypeAdapter(Activity.class, new ActivityDeserializer())
                .registerTypeAdapter(Location.class, new LocationDeserializer())
                .registerTypeAdapter(Country.class, new CountryDeserializer())

                // Tell Gson to skip any BufferedImage fields.
                // Images are binary data and don't belong in a text-based JSON file.
                .setExclusionStrategies(new ExclusionStrategy() {
                    /**
                     * Skips BufferedImage fields during JSON serialization.
                     */
                    @Override
                    public boolean shouldSkipField(FieldAttributes field) {
                        return field.getDeclaredType() == BufferedImage.class;
                    }

                    /**
                     * Skips BufferedImage class types during JSON serialization.
                     */
                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return clazz == BufferedImage.class;
                    }
                })

                .create();
    }

    // ========================================================================
    // PUBLIC METHODS - These are what the rest of the app calls
    // ========================================================================

    /**
     * Saves a list of trips to the JSON file.
     *
     * What happens step by step:
     * 1. Create the "data/" folder if it doesn't exist yet
     * 2. Convert all Trip objects into a JSON string using Gson
     * 3. Write that string to "data/trips.json"
     *
     * @param trips the list of trips to save
     * @throws IOException if writing to the file fails
     */
    public void save(List<Trip> trips) throws IOException {
        // Ensure the data directory exists
        Path directory = dataFilePath.getParent();
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        // Write the JSON to the file
        // try-with-resources automatically closes the writer when done
        try (Writer writer = Files.newBufferedWriter(dataFilePath)) {
            gson.toJson(trips, writer);
        }
    }

    /**
     * Loads trips from the JSON file.
     *
     * What happens step by step:
     * 1. Check if "data/trips.json" exists
     * 2. If not, return an empty list (first time running the app)
     * 3. If yes, read the file and convert JSON back into Trip objects
     *
     * @return the list of trips loaded from the file, or empty list if no file
     * @throws IOException if reading the file fails
     */
    public List<Trip> load() throws IOException {
        if (!Files.exists(dataFilePath)) {
            // No saved data yet - this is fine, just return empty
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(dataFilePath)) {
            // TypeToken tells Gson we want a List<Trip>, not just a single Trip.
            // This is needed because of Java's "type erasure" - at runtime,
            // Java forgets the <Trip> part, so we use TypeToken to preserve it.
            Type listType = new TypeToken<List<Trip>>() {}.getType();
            List<Trip> trips = gson.fromJson(reader, listType);
            return trips != null ? trips : new ArrayList<>();
        } catch (JsonParseException e) {
            // If the file is corrupted or has invalid JSON,
            // print a warning and return empty rather than crashing
            System.err.println("Warning: Could not parse saved data: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns the path where data is being stored.
     * Useful for displaying to the user or for debugging.
     */
    public Path getDataFilePath() {
        return dataFilePath;
    }

    // ========================================================================
    // CUSTOM DESERIALIZERS
    // ========================================================================
    // These teach Gson how to reconstruct Trip and Activity objects
    // from JSON. We need these because the constructors have validation
    // (e.g., startDateTime must be before endDateTime), and Gson's default
    // approach would bypass that validation.

    /**
     * Serializer for Trip that stores references instead of nested country payload.
     */
    private static class TripSerializer implements JsonSerializer<Trip> {
        /**
         * Converts a trip into its JSON representation.
         */
        @Override
        public JsonElement serialize(Trip src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("name", src.getName());
            obj.addProperty("priority", src.getPriority());
            obj.add("startDateTime", context.serialize(src.getStartDateTime(), LocalDateTime.class));
            obj.add("endDateTime", context.serialize(src.getEndDateTime(), LocalDateTime.class));
            obj.addProperty("countryId", src.getCountry() != null ? src.getCountry().getId() : 0);
            if (src.getDescription() != null) {
                obj.addProperty("description", src.getDescription());
            }
            obj.add("activities", context.serialize(src.getActivities()));
            JsonArray expenseIds = new JsonArray();
            for (Expense expense : src.getExpenses()) {
                expenseIds.add(expense.getId());
            }
            obj.add("expenseIds", expenseIds);
            return obj;
        }
    }

    /**
     * Custom deserializer for Trip objects.
     *
     * When Gson reads a Trip from JSON, instead of trying to magically
     * create one, it calls this code which properly uses the Trip constructor.
     */
    private static class TripDeserializer implements JsonDeserializer<Trip> {
        /**
         * Converts JSON into a trip instance.
         */
        @Override
        public Trip deserialize(JsonElement json, Type typeOfT,
                                JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            // Extract the basic fields from the JSON object
            int id = obj.get("id").getAsInt();
            String name = getString(obj, "name", "Untitled Trip");

            // Parse dates using our LocalDateTime adapter
            LocalDateTime start = context.deserialize(obj.get("startDateTime"), LocalDateTime.class);
            LocalDateTime end = context.deserialize(obj.get("endDateTime"), LocalDateTime.class);

            Country country = null;
            if (obj.has("countryId") && !obj.get("countryId").isJsonNull()) {
                country = new Country(obj.get("countryId").getAsInt(), "Unspecified");
            }
            if (obj.has("country") && !obj.get("country").isJsonNull()) {
                country = context.deserialize(obj.get("country"), Country.class);
            }
            // Backward compatibility with older trip JSON using location.
            if (country == null && obj.has("location") && !obj.get("location").isJsonNull()) {
                Location oldLocation = context.deserialize(obj.get("location"), Location.class);
                if (oldLocation != null) {
                    country = oldLocation.getCountry();
                }
            }
            if (country == null) {
                country = new Country(0, "Unspecified");
            }

            // Create the Trip using its proper constructor (with validation)
            Trip trip = new Trip(id, name, start, end, country);

            if (obj.has("priority") && !obj.get("priority").isJsonNull()) {
                try {
                    trip.setPriority(Math.max(0, obj.get("priority").getAsInt()));
                } catch (Exception ignored) {
                    trip.setPriority(0);
                }
            }

            // Set optional description
            if (obj.has("description") && !obj.get("description").isJsonNull()) {
                trip.setDescription(obj.get("description").getAsString());
            }

            // Restore activities
            if (obj.has("activities")) {
                JsonArray activitiesArray = obj.getAsJsonArray("activities");
                for (JsonElement actElement : activitiesArray) {
                    Activity activity = context.deserialize(actElement, Activity.class);
                    try {
                        trip.addActivity(activity);
                    } catch (Exception e) {
                        System.err.println("Warning: Skipping activity due to: " + e.getMessage());
                    }
                }
            }

            if (obj.has("expenseIds")) {
                JsonArray expenseIds = obj.getAsJsonArray("expenseIds");
                for (JsonElement expenseIdElement : expenseIds) {
                    int expenseId = expenseIdElement.getAsInt();
                    trip.addExpense(createExpensePlaceholder(expenseId));
                }
            } else if (obj.has("expenses")) {
                // Backward compatibility with embedded expense payloads.
                JsonArray expensesArray = obj.getAsJsonArray("expenses");
                for (JsonElement expElement : expensesArray) {
                    Expense expense = context.deserialize(expElement, Expense.class);
                    trip.addExpense(expense);
                }
            }

            return trip;
        }

        private String getString(JsonObject obj, String key, String defaultValue) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return defaultValue;
            }
            String value = obj.get(key).getAsString();
            return value == null || value.trim().isEmpty() ? defaultValue : value;
        }
    }

    /**
     * Serializer for Activity that stores location references by id.
     */
    private static class ActivitySerializer implements JsonSerializer<Activity> {
        /**
         * Converts an activity into its JSON representation.
         */
        @Override
        public JsonElement serialize(Activity src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("name", src.getName());
            obj.addProperty("priority", src.getPriority());
            obj.add("startDateTime", context.serialize(src.getStartDateTime(), LocalDateTime.class));
            obj.add("endDateTime", context.serialize(src.getEndDateTime(), LocalDateTime.class));
            if (src.getDescription() != null) {
                obj.addProperty("description", src.getDescription());
            }
            obj.add("types", context.serialize(src.getTypes()));
            JsonArray expenseIds = new JsonArray();
            for (Expense expense : src.getExpenses()) {
                expenseIds.add(expense.getId());
            }
            obj.add("expenseIds", expenseIds);
            obj.addProperty("locationId", src.getLocation() != null ? src.getLocation().getId() : 0);
            return obj;
        }
    }

    /**
     * Custom deserializer for Activity objects.
    * Same idea as TripDeserializer - we need to call the constructor properly.
     */
    private static class ActivityDeserializer implements JsonDeserializer<Activity> {
        /**
         * Converts JSON into an activity instance.
         */
        @Override
        public Activity deserialize(JsonElement json, Type typeOfT,
                                    JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            int id = obj.get("id").getAsInt();
            String name = obj.get("name").getAsString();
            LocalDateTime start = context.deserialize(obj.get("startDateTime"), LocalDateTime.class);
            LocalDateTime end = context.deserialize(obj.get("endDateTime"), LocalDateTime.class);

            Location location = null;
            if (obj.has("locationId") && !obj.get("locationId").isJsonNull()) {
                int locationId = obj.get("locationId").getAsInt();
                location = new Location(locationId, "Unspecified", null, null,
                        new Country(0, "Unspecified"), null, null, null);
            }
            if (obj.has("location") && !obj.get("location").isJsonNull()) {
                location = context.deserialize(obj.get("location"), Location.class);
            }

            Activity activity = new Activity(id, name, start, end, location);

            if (obj.has("priority") && !obj.get("priority").isJsonNull()) {
                try {
                    activity.setPriority(Math.max(0, obj.get("priority").getAsInt()));
                } catch (Exception ignored) {
                    activity.setPriority(0);
                }
            }

            if (obj.has("description") && !obj.get("description").isJsonNull()) {
                activity.setDescription(obj.get("description").getAsString());
            }

            // Restore activity types (SIGHTSEEING, ADVENTURE, etc.)
            if (obj.has("types")) {
                JsonArray typesArray = obj.getAsJsonArray("types");
                for (JsonElement typeElement : typesArray) {
                    activity.addType(Activity.Type.valueOf(typeElement.getAsString()));
                }
            }

            if (obj.has("expenseIds")) {
                JsonArray expenseIds = obj.getAsJsonArray("expenseIds");
                for (JsonElement expenseIdElement : expenseIds) {
                    int expenseId = expenseIdElement.getAsInt();
                    activity.addExpense(createExpensePlaceholder(expenseId));
                }
            } else if (obj.has("expenses")) {
                // Backward compatibility with embedded expense payloads.
                JsonArray expensesArray = obj.getAsJsonArray("expenses");
                for (JsonElement expElement : expensesArray) {
                    Expense expense = context.deserialize(expElement, Expense.class);
                    activity.addExpense(expense);
                }
            }

            return activity;
        }
    }

    /**
     * Custom deserializer for Location with optional fields and backwards compatibility.
     */
    private static class LocationDeserializer implements JsonDeserializer<Location> {
        /**
         * Converts JSON into a location instance.
         */
        @Override
        public Location deserialize(JsonElement json, Type typeOfT,
                                    JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            int id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
            String name = getString(obj, "name", "Unnamed Location");
            String address = getStringOrNull(obj, "address");
            String city = getStringOrNull(obj, "city");
            Country country = null;
            if (obj.has("countryId") && !obj.get("countryId").isJsonNull()) {
                country = new Country(obj.get("countryId").getAsInt(), "Unspecified");
            }
            if (obj.has("country") && !obj.get("country").isJsonNull()) {
                JsonElement countryElement = obj.get("country");
                if (countryElement.isJsonObject()) {
                    country = context.deserialize(countryElement, Country.class);
                } else {
                    String countryName = countryElement.getAsString();
                    country = new Country(0, countryName == null || countryName.isBlank() ? "Unspecified" : countryName);
                }
            }
            if (country == null) {
                country = new Country(0, "Unspecified");
            }
            Double latitude = getDoubleOrNull(obj, "latitude");
            Double longitude = getDoubleOrNull(obj, "longitude");
            String imagePath = IMAGE_ASSET_STORE.normalizeImagePath(getStringOrNull(obj, "imagePath"));

            return new Location(id, name, address, city, country, latitude, longitude, imagePath);
        }

        private String getString(JsonObject obj, String key, String defaultValue) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return defaultValue;
            }
            return obj.get(key).getAsString();
        }

        private String getStringOrNull(JsonObject obj, String key) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return null;
            }
            String value = obj.get(key).getAsString();
            return value.isBlank() ? null : value;
        }

        private Double getDoubleOrNull(JsonObject obj, String key) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) {
                return null;
            }
            try {
                return obj.get(key).getAsDouble();
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Deserializer for country with safe fallbacks.
     */
    private static class CountryDeserializer implements JsonDeserializer<Country> {
        /**
         * Converts JSON into a country instance.
         */
        @Override
        public Country deserialize(JsonElement json, Type typeOfT,
                                   JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                String name = json.getAsString();
                return new Country(0, (name == null || name.isBlank()) ? "Unspecified" : name);
            }

            JsonObject obj = json.getAsJsonObject();
            int id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "Unspecified";
            if (name.isBlank()) {
                name = "Unspecified";
            }
            String continent = obj.has("continent") && !obj.get("continent").isJsonNull() ? obj.get("continent").getAsString() : null;
            String imagePath = obj.has("imagePath") && !obj.get("imagePath").isJsonNull()
                    ? IMAGE_ASSET_STORE.normalizeImagePath(obj.get("imagePath").getAsString()) : null;
            return new Country(id, name, continent, imagePath);
        }
    }

    private static Expense createExpensePlaceholder(int expenseId) {
        return new Expense(expenseId, "Unspecified", 0f, Expense.Currency.USD, Expense.Type.OTHER);
    }
}
