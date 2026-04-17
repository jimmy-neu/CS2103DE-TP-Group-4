package storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import country.Country;

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
 * Persistence adapter for reading and writing country data as JSON.
 *
 * <p>This class is used by {@link country.CountryRepository} and encapsulates Gson-based file
 * IO so repository logic remains independent from serialization details.</p>
 */
public class CountryStorage {

    private static final String DATA_DIRECTORY = "data";
    private static final String DATA_FILE = "countries.json";

    private final Gson gson;
    private final Path dataFilePath;

    /**
     * Creates storage bound to the default country data file.
     */
    public CountryStorage() {
        this(Paths.get(DATA_DIRECTORY, DATA_FILE));
    }

    /**
     * Creates storage bound to a specific data file path.
     *
     * @param dataFilePath target JSON data path
     */
    public CountryStorage(Path dataFilePath) {
        this.dataFilePath = dataFilePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Persists countries to the configured JSON file.
     *
     * @param countries countries to persist
     * @throws IOException if writing fails
     */
    public void save(List<Country> countries) throws IOException {
        Path directory = dataFilePath.getParent();
        if (directory != null && !Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        try (Writer writer = Files.newBufferedWriter(dataFilePath)) {
            gson.toJson(countries, writer);
        }
    }

    /**
     * Loads countries from the configured JSON file.
     *
     * @return loaded countries, or an empty list when no data is present
     * @throws IOException if reading fails
     */
    public List<Country> load() throws IOException {
        if (!Files.exists(dataFilePath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(dataFilePath)) {
            Type listType = new TypeToken<List<Country>>() {}.getType();
            List<Country> countries = gson.fromJson(reader, listType);
            return countries != null ? countries : new ArrayList<>();
        } catch (JsonParseException e) {
            // Recover from partially written/corrupted JSON by resetting to defaults upstream.
            return new ArrayList<>();
        }
    }
}
