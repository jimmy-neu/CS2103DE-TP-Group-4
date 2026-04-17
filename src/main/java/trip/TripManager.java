package trip;
import activity.Activity;
import country.Country;
import country.CountryRepository;
import expense.Expense;
import expense.ExpenseRepository;
import exceptions.TimeIntervalConflictException;
import exceptions.TripNotFoundException;
import location.Location;
import location.LocationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import storage.JsonStorage;
import java.io.IOException;

/**
 * Application service that manages the lifecycle of all {@link Trip} entities.
 *
 * <p>This manager validates trip constraints, coordinates persistence through
 * {@link JsonStorage}, and resolves linked country, location, and expense references.</p>
 */
public class TripManager {

    private static final Set<Integer> USED_TRIP_IDS = new HashSet<>();
    private static final Set<String> USED_TRIP_NAMES = new HashSet<>();

    private final List<Trip> trips = new ArrayList<>();
    private final Map<Integer, Trip> tripsById = new HashMap<>();
    // Added JsonStorage attribute
    private final JsonStorage storage;
    private int nextId = 1;

    /**
     * Creates a trip manager using default JSON storage.
     */
    public TripManager() {
        //this(Collections.emptyList()); prev version
        this(new JsonStorage());
    }


//    public TripManager(List<Trip> trips) {
//        if (trips != null) {
//            this.trips.addAll(trips);
//        }
//    }

    /**
     * Creates a trip manager with an explicit storage gateway.
     *
     * @param storage trip storage gateway
     */
    public TripManager(JsonStorage storage) {
        this.storage = storage;
    }

    /**
     * Creates a TripManager with pre-loaded trips (used internally by load).
     */
    public TripManager(List<Trip> trips) {
        this.storage = new JsonStorage();
        if (trips != null) {
            for (Trip trip : trips) {
                registerTripIdentity(trip);
                this.trips.add(trip);
                this.tripsById.put(trip.getId(), trip);
                this.nextId = Math.max(this.nextId, trip.getId() + 1);
            }
        }
    }

    /**
     * Loads trips from storage without external reference resolution.
     *
     * @throws IOException if reading the file fails
     */
    public void loadFromFile() throws IOException {
        loadFromFile(null, null, null);
    }

    /**
     * Loads trips from storage with optional country/location reference resolution.
     *
     * @param countryRepository country repository, or {@code null}
     * @param locationRepository location repository, or {@code null}
     * @throws IOException if reading the file fails
     */
    public void loadFromFile(CountryRepository countryRepository, LocationRepository locationRepository) throws IOException {
        loadFromFile(countryRepository, locationRepository, null);
    }

    /**
     * Loads trips from storage and optionally resolves country/location/expense references.
     *
     * @param countryRepository country repository, or {@code null}
     * @param locationRepository location repository, or {@code null}
     * @param expenseRepository expense repository, or {@code null}
     * @throws IOException if reading the file fails
     */
    public void loadFromFile(CountryRepository countryRepository, LocationRepository locationRepository,
                             ExpenseRepository expenseRepository) throws IOException {
        List<Trip> loaded = storage.load();
        trips.clear();
        tripsById.clear();
        USED_TRIP_IDS.clear();
        USED_TRIP_NAMES.clear();
        nextId = 1;

        for (Trip trip : loaded) {
            registerTripIdentity(trip);
            trips.add(trip);
            tripsById.put(trip.getId(), trip);
            nextId = Math.max(nextId, trip.getId() + 1);
        }

        if (countryRepository != null || locationRepository != null || expenseRepository != null) {
            resolveReferences(countryRepository, locationRepository, expenseRepository);
        }
    }

    /**
     * Persists all trips to storage.
     *
     * @throws IOException if writing the file fails
     */
    public void saveToFile() throws IOException {
        storage.save(trips);
    }

    /**
     * Returns an immutable snapshot of all trips.
     *
     * @return all managed trips
     */
    public List<Trip> getTrips() {
        return Collections.unmodifiableList(trips);
    }

    /**
     * Adds a trip after uniqueness and overlap validation.
     *
     * @param trip trip to add
     * @throws TimeIntervalConflictException if the trip overlaps an existing trip
     */
    public void addTrip(Trip trip) throws TimeIntervalConflictException {
        Objects.requireNonNull(trip, "trip");
        ensureUniqueTripName(trip.getName());
        ensureUniqueTripId(trip.getId());
        for (Trip existing : trips) {
            if (trip.overlapsWith(existing)) {
                throw new TimeIntervalConflictException(
                        "Trip time conflict with existing trip: " + existing.getName());
            }
        }
        registerTripIdentity(trip);
        trips.add(trip);
        tripsById.put(trip.getId(), trip);
        nextId = Math.max(nextId, trip.getId() + 1);
    }

    /**
     * Updates core fields of an existing trip.
     *
     * @param tripId target trip id
     * @param name required trip name
     * @param startDateTime required start timestamp
     * @param endDateTime required end timestamp
     * @param country required country
     * @throws TimeIntervalConflictException if the updated range overlaps another trip
     */
    public void updateTrip(int tripId, String name, LocalDateTime startDateTime,
                           LocalDateTime endDateTime, Country country) throws TimeIntervalConflictException {
        Trip trip = tripsById.get(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: id=" + tripId);
        }
        Objects.requireNonNull(country, "country");

        String normalizedName = normalizeRequiredName(name, "trip name");
        String oldNameKey = normalizeNameKey(trip.getName());
        String newNameKey = normalizeNameKey(normalizedName);

        for (Trip other : trips) {
            if (other.getId() != tripId && normalizeNameKey(other.getName()).equals(newNameKey)) {
                throw new IllegalArgumentException("Trip already exists: " + normalizedName);
            }
        }

        if (startDateTime == null || endDateTime == null) {
            throw new IllegalArgumentException("Trip start and end are required");
        }
        if (startDateTime.isAfter(endDateTime)) {
            throw new IllegalArgumentException("Trip start must not be after end");
        }

        for (Trip other : trips) {
            if (other.getId() == tripId) {
                continue;
            }
            if (startDateTime.isBefore(other.getEndDateTime()) && endDateTime.isAfter(other.getStartDateTime())) {
                throw new TimeIntervalConflictException("Trip time conflict with existing trip: " + other.getName());
            }
        }

        if (!oldNameKey.equals(newNameKey)) {
            USED_TRIP_NAMES.remove(oldNameKey);
            USED_TRIP_NAMES.add(newNameKey);
        }

        trip.setName(normalizedName);
        trip.setStartDateTime(startDateTime);
        trip.setEndDateTime(endDateTime);
        trip.setCountry(country);
    }

    /**
     * Deletes a trip by identifier.
     *
     * @param id trip id
     * @throws TripNotFoundException if no matching trip exists
     */
    public void deleteTripById(int id) throws TripNotFoundException {
        Trip trip = findTripById(id);
        trips.remove(trip);
        unregisterTripIdentity(trip);
        tripsById.remove(id);
    }

    /**
     * Deletes a trip by name.
     *
     * @param name trip name
     * @throws TripNotFoundException if no matching trip exists
     */
    public void deleteTripByName(String name) throws TripNotFoundException {
        Trip trip = findTripByName(name);
        trips.remove(trip);
        unregisterTripIdentity(trip);
        tripsById.remove(trip.getId());
    }

    /**
     * Returns the next unused trip identifier.
     *
     * @return next available trip id
     */
    public int nextAvailableId() {
        while (USED_TRIP_IDS.contains(nextId)) {
            nextId++;
        }
        return nextId;
    }

    /**
     * Returns a trip by identifier.
     *
     * @param id trip id
     * @return matching trip
     * @throws TripNotFoundException if no matching trip exists
     */
    public Trip getTripById(int id) throws TripNotFoundException {
        return findTripById(id);
    }

    /**
     * Returns a trip by name.
     *
     * @param name trip name
     * @return matching trip
     * @throws TripNotFoundException if no matching trip exists
     */
    public Trip getTripByName(String name) throws TripNotFoundException {
        return findTripByName(name);
    }

    /**
     * Returns all trips that overlap at least one other trip.
     *
     * @return overlapping trips
     */
    public List<Trip> getOverlappingTrips() {
        List<Trip> result = new ArrayList<>();
        for (int i = 0; i < trips.size(); i++) {
            Trip current = trips.get(i);
            for (int j = i + 1; j < trips.size(); j++) {
                Trip other = trips.get(j);
                if (current.overlapsWith(other)) {
                    if (!result.contains(current)) {
                        result.add(current);
                    }
                    if (!result.contains(other)) {
                        result.add(other);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns trips overlapping the supplied time window.
     *
     * @param begin inclusive window start
     * @param end exclusive window end
     * @return matching trips
     */
    public List<Trip> getOverlappingTrips(LocalDateTime begin, LocalDateTime end) {
        Objects.requireNonNull(begin, "begin");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(begin)) {
            throw new IllegalArgumentException("end must not be before begin");
        }
        List<Trip> result = new ArrayList<>();
        for (Trip trip : trips) {
            if (trip.getStartDateTime().isBefore(end) && trip.getEndDateTime().isAfter(begin)) {
                result.add(trip);
            }
        }
        return result;
    }

    private Trip findTripById(int id) throws TripNotFoundException {
        Trip trip = tripsById.get(id);
        if (trip != null) {
            return trip;
        }
        throw new TripNotFoundException("Trip not found: id=" + id);
    }

    private Trip findTripByName(String name) throws TripNotFoundException {
        for (Trip trip : trips) {
            if (Objects.equals(trip.getName(), name)) {
                return trip;
            }
        }
        throw new TripNotFoundException("Trip not found: name=" + name);
    }

    private void ensureUniqueTripName(String name) {
        String normalizedName = normalizeNameKey(name);
        if (USED_TRIP_NAMES.contains(normalizedName)) {
            throw new IllegalArgumentException("Trip already exists: " + name);
        }
    }

    private void ensureUniqueTripId(int id) {
        if (USED_TRIP_IDS.contains(id)) {
            throw new IllegalArgumentException("Duplicate trip id detected: " + id);
        }
    }

    private void registerTripIdentity(Trip trip) {
        String nameKey = normalizeNameKey(trip.getName());
        if (!USED_TRIP_IDS.add(trip.getId())) {
            throw new IllegalStateException("Duplicate trip id detected: " + trip.getId());
        }
        if (!USED_TRIP_NAMES.add(nameKey)) {
            throw new IllegalStateException("Duplicate trip name detected: " + trip.getName());
        }
    }

    private void unregisterTripIdentity(Trip trip) {
        USED_TRIP_IDS.remove(trip.getId());
        USED_TRIP_NAMES.remove(normalizeNameKey(trip.getName()));
    }

    private String normalizeNameKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredName(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private void resolveReferences(CountryRepository countryRepository, LocationRepository locationRepository,
                                   ExpenseRepository expenseRepository) {
        for (Trip trip : trips) {
            if (countryRepository != null && trip.getCountry() != null) {
                Country resolvedCountry = countryRepository.findById(trip.getCountry().getId());
                if (resolvedCountry == null) {
                    resolvedCountry = countryRepository.findByName(trip.getCountry().getName());
                }
                if (resolvedCountry != null) {
                    trip.setCountry(resolvedCountry);
                }
            }

            if (expenseRepository != null) {
                trip.setExpenses(resolveExpenseReferences(trip.getExpenses(), expenseRepository));
            }

            if (locationRepository == null) {
                if (expenseRepository != null) {
                    for (Activity activity : trip.getActivities()) {
                        activity.setExpenses(resolveExpenseReferences(activity.getExpenses(), expenseRepository));
                    }
                }
                continue;
            }
            for (Activity activity : trip.getActivities()) {
                Location location = activity.getLocation();
                if (location == null) {
                    continue;
                }
                Location resolvedLocation = locationRepository.findById(location.getId());
                if (resolvedLocation == null) {
                    resolvedLocation = locationRepository.findByName(location.getName());
                }
                if (resolvedLocation != null) {
                    activity.setLocation(resolvedLocation);
                }
                if (expenseRepository != null) {
                    activity.setExpenses(resolveExpenseReferences(activity.getExpenses(), expenseRepository));
                }
            }
        }
    }

    private List<Expense> resolveExpenseReferences(List<Expense> source, ExpenseRepository expenseRepository) {
        List<Expense> resolved = new ArrayList<>();
        for (Expense expense : source) {
            Expense canonical = expenseRepository.findById(expense.getId());
            if (canonical != null) {
                resolved.add(canonical);
                continue;
            }
            expenseRepository.registerExpense(expense);
            resolved.add(expense);
        }
        return resolved;
    }

}
