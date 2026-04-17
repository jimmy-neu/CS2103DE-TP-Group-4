# API Documentation

This document describes the production API surface in `src/main/java`.

Scope:
- All public classes, interfaces, and enums.
- Constructors and public methods.
- Key inherited API from shared base abstractions.

## Package: default

### `Launcher`
Responsibility:
- JVM entrypoint that delegates startup to JavaFX.

Constructors:
- `Launcher()` - default constructor.

Methods:
- `main(String[] args)` - launches the JavaFX application (`Main`).

### `Main`
Responsibility:
- JavaFX bootstrap class that loads the root view and theme.

Constructors:
- `Main()` - default constructor.

Methods:
- `start(Stage stage)` - loads `MainWindow.fxml`, applies stylesheet, and shows the stage.

## Package: `utilities`

### `BaseEntity` (abstract)
Responsibility:
- Shared identity and metadata model used by domain entities.

Constructors:
- `BaseEntity()` (protected) - initializes with default id/name.
- `BaseEntity(int id, String name)` (protected) - initializes explicit id/name with validation.

Methods:
- `getId()` - returns entity id.
- `setId(int id)` - sets entity id (non-negative).
- `getName()` - returns display name.
- `setName(String name)` - sets display name (non-blank).
- `getDescription()` - returns optional description.
- `setDescription(String description)` - sets optional description.
- `getPriority()` - returns priority for sorting/presentation.
- `setPriority(int priority)` - sets non-negative priority.
- `getImage()` - returns transient runtime image.
- `setImage(BufferedImage image)` - sets transient runtime image.

### `Copyable` (interface)
Responsibility:
- Contract for object copy operations.

Methods:
- `copy()` - returns a logical copy of the instance (generic return type `T`).

## Package: `temporal`

### `TimeInterval` (interface)
Responsibility:
- Contract for start/end time entities with overlap and duration helpers.

Methods:
- `getStartDateTime()` - returns interval start.
- `setStartDateTime(LocalDateTime startDateTime)` - updates interval start.
- `getEndDateTime()` - returns interval end.
- `setEndDateTime(LocalDateTime endDateTime)` - updates interval end.
- `overlapsWith(TimeInterval other)` (default) - checks time overlap.
- `getDuration()` (default) - returns elapsed `Duration`.
- `getPeriod()` (default) - returns elapsed calendar `Period`.

## Package: `exceptions`

### `ActivityNotFoundException`
Responsibility:
- Raised when an activity lookup/delete target cannot be found.

Constructors:
- `ActivityNotFoundException(String message)` - creates exception with detail message.

### `ExpenseNotFoundException`
Responsibility:
- Raised when an expense lookup/delete target cannot be found.

Constructors:
- `ExpenseNotFoundException(String message)` - creates exception with detail message.

### `TimeIntervalConflictException`
Responsibility:
- Raised when scheduling operations violate non-overlap constraints.

Constructors:
- `TimeIntervalConflictException(String message)` - creates exception with detail message.

### `TripNotFoundException`
Responsibility:
- Raised when a trip lookup/delete target cannot be found.

Constructors:
- `TripNotFoundException(String message)` - creates exception with detail message.

## Package: `activity`

### `Activity`
Responsibility:
- Domain model for one scheduled activity in a trip itinerary.

Public enums:
- `Activity.Type`: `SIGHTSEEING`, `ADVENTURE`, `RELAXATION`, `CULTURAL`, `OTHER`.

Constructors:
- `Activity(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime)`.
- `Activity(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime, Location location)`.

Methods:
- `getTypes()` - returns immutable activity type list.
- `setTypes(List<Activity.Type> types)` - replaces activity type list.
- `addType(Activity.Type type)` - appends a type label.
- `getExpenses()` - returns immutable attached expense list.
- `setExpenses(List<Expense> expenses)` - replaces attached expense list.
- `getLocation()` - returns assigned location.
- `setLocation(Location location)` - updates assigned location.
- `getStartDateTime()` - returns activity start.
- `setStartDateTime(LocalDateTime startDateTime)` - updates start with range validation.
- `getEndDateTime()` - returns activity end.
- `setEndDateTime(LocalDateTime endDateTime)` - updates end with range validation.
- `addExpense(Expense expense)` - attaches an expense.
- `deleteExpenseById(int id)` - removes expense by id.
- `deleteExpenseByName(String name)` - removes expense by name.
- `getExpenseById(int id)` - fetches expense by id.
- `getExpenseByName(String name)` - fetches expense by name.
- `getTotalCost(Expense.Currency currency)` - sums costs in one currency.
- `copy()` - creates a deep copy of activity and direct expenses.
- `toString()` - returns formatted activity summary text.

## Package: `country`

### `Country`
Responsibility:
- Domain model representing a country used by trips and locations.

Constructors:
- `Country(int id, String name)`.
- `Country(int id, String name, String continent, String imagePath)`.

Methods:
- `getContinent()` - returns optional continent label.
- `setContinent(String continent)` - updates optional continent label.
- `getImagePath()` - returns normalized image path.
- `setImagePath(String imagePath)` - updates normalized image path.
- `toString()` - returns display-friendly country text.

### `CountryRepository`
Responsibility:
- In-memory and persistent lifecycle service for countries.

Constructors:
- `CountryRepository()` - default storage wiring.
- `CountryRepository(CountryStorage storage, ImageAssetStore imageAssetStore)` - explicit dependencies.

Methods:
- `load()` - loads countries and rebuilds identity indexes.
- `save()` - persists all countries.
- `getCountries()` - returns immutable country view.
- `addCountry(String name, String continent, String imageSourcePath)` - creates and registers country.
- `updateCountry(int countryId, String name, String continent, String imageSourcePath)` - updates existing country.
- `findById(int countryId)` - finds by id.
- `findByName(String name)` - finds by name (case-insensitive).
- `deleteCountryById(int countryId)` - deletes by id.

## Package: `expense`

### `Expense`
Responsibility:
- Domain model for one monetary expense entry.

Public enums:
- `Expense.Type`: `FOOD`, `ACCOMMODATION`, `TRANSPORTATION`, `ENTERTAINMENT`, `OTHER`.
- `Expense.Currency`: `SGD`, `USD`, `EUR`, `GBP`, `JPY`, `CNY`.

Constructors:
- `Expense(int id, String name, float cost, Expense.Currency currency, Expense.Type type)`.
- `Expense(int id, String name, float cost, Expense.Currency currency, Expense.Type type, String imagePath)`.

Methods:
- `getCost()` - returns expense amount.
- `setCost(float cost)` - updates expense amount (non-negative).
- `getCurrency()` - returns currency.
- `setCurrency(Expense.Currency currency)` - updates currency.
- `getType()` - returns expense category.
- `setType(Expense.Type type)` - updates expense category.
- `getImagePath()` - returns normalized image path.
- `setImagePath(String imagePath)` - updates normalized image path.
- `copy()` - returns copied expense.
- `toString()` - returns formatted expense summary text.

### `ExpenseManagable` (interface)
Responsibility:
- Contract for owning objects that manage expenses.

Methods:
- `addExpense(Expense expense)`.
- `deleteExpenseById(int id)`.
- `deleteExpenseByName(String name)`.
- `getExpenseById(int id)`.
- `getExpenseByName(String name)`.
- `getTotalCost(Expense.Currency currency)`.

### `ExpenseRepository`
Responsibility:
- In-memory and persistent lifecycle service for expenses.

Constructors:
- `ExpenseRepository()` - default storage wiring.
- `ExpenseRepository(ExpenseStorage storage, ImageAssetStore imageAssetStore)` - explicit dependencies.

Methods:
- `load()` - loads expenses and rebuilds identity indexes.
- `save()` - persists all expenses.
- `getExpenses()` - returns immutable expense view.
- `findById(int id)` - finds by id.
- `nextAvailableId()` - returns next unused id.
- `createExpense(String name, float cost, Expense.Currency currency, Expense.Type type, String imageSourcePath)` - creates and registers expense.
- `updateExpense(int expenseId, String name, float cost, Expense.Currency currency, Expense.Type type, String imageSourcePath)` - updates existing expense.
- `deleteExpenseById(int expenseId)` - deletes by id.
- `deleteExpenseByName(String name)` - deletes by exact name.
- `registerExpense(Expense expense)` - registers existing expense instance.

## Package: `filter`

### `ActivityFilter`
Responsibility:
- Stateless activity filtering utility.

Methods:
- `byType(List<Activity> activities, Activity.Type type)` - returns activities that match a type (or all when type is null).

## Package: `location`

### `Location`
Responsibility:
- Domain model for a physical place attached to activities.

Constructors:
- `Location(int id, String name)`.
- `Location(int id, String name, String address, String city, Country country, Double latitude, Double longitude)`.
- `Location(int id, String name, String address, String city, Country country, Double latitude, Double longitude, String imagePath)`.

Methods:
- `getAddress()` - returns optional address.
- `setAddress(String address)` - updates optional address.
- `getCity()` - returns optional city.
- `setCity(String city)` - updates optional city.
- `getCountry()` - returns country reference.
- `setCountry(Country country)` - updates country reference.
- `getLatitude()` - returns optional latitude.
- `setLatitude(Double latitude)` - updates optional latitude.
- `getLongitude()` - returns optional longitude.
- `setLongitude(Double longitude)` - updates optional longitude.
- `getImagePath()` - returns normalized image path.
- `setImagePath(String imagePath)` - updates normalized image path.
- `distanceTo(Location other)` - computes geodesic distance (km) using Haversine formula.
- `toString()` - returns display-friendly location summary text.

### `LocationRepository`
Responsibility:
- In-memory and persistent lifecycle service for locations.

Constructors:
- `LocationRepository(CountryRepository countryRepository)` - default storage wiring.
- `LocationRepository(LocationStorage storage, CountryRepository countryRepository, ImageAssetStore imageAssetStore)` - explicit dependencies.

Methods:
- `load()` - loads locations, resolves country links, and rebuilds identity indexes.
- `save()` - persists all locations.
- `getLocations()` - returns immutable location view.
- `findById(int locationId)` - finds by id.
- `findByName(String name)` - finds by name (case-insensitive).
- `deleteLocationById(int locationId)` - deletes by id.
- `addLocation(String name, String address, String city, int countryId, Double latitude, Double longitude, String imageSourcePath)` - creates and registers location.
- `updateLocation(int locationId, String name, String address, String city, int countryId, Double latitude, Double longitude, String imageSourcePath)` - updates existing location.

## Package: `storage`

### `CountryStorage`
Responsibility:
- JSON adapter for reading/writing countries.

Constructors:
- `CountryStorage()` - default file path (`data/countries.json`).
- `CountryStorage(Path dataFilePath)` - explicit file path.

Methods:
- `save(List<Country> countries)` - writes country list.
- `load()` - reads country list.

### `ExpenseStorage`
Responsibility:
- JSON adapter for reading/writing expenses.

Constructors:
- `ExpenseStorage()` - default file path (`data/expenses.json`).
- `ExpenseStorage(Path dataFilePath)` - explicit file path.

Methods:
- `save(List<Expense> expenses)` - writes expense list.
- `load()` - reads expense list.

### `ImageAssetStore`
Responsibility:
- Image import and normalization utility used by repositories.

Constructors:
- `ImageAssetStore()` - default constructor.

Methods:
- `importImage(String sourcePath, String keyPrefix)` - imports image using prefix-based naming.
- `importImage(String sourcePath, String keyPrefix, String semanticName)` - imports image with semantic naming hint.
- `normalizeImagePath(String imagePath)` - canonicalizes image paths and legacy mappings.

### `JsonStorage`
Responsibility:
- Primary JSON gateway for trip aggregate persistence.

Constructors:
- `JsonStorage()` - default file path (`data/trips.json`).
- `JsonStorage(Path dataFilePath)` - explicit file path.

Methods:
- `save(List<Trip> trips)` - writes trip list.
- `load()` - reads trip list with custom serializer/deserializer adapters.
- `getDataFilePath()` - returns active data file path.

### `LocalDateTimeAdapter`
Responsibility:
- Gson type adapter for `LocalDateTime` serialization/deserialization.

Constructors:
- `LocalDateTimeAdapter()` - default constructor.

Methods:
- `write(JsonWriter out, LocalDateTime value)` - serializes datetime to ISO text.
- `read(JsonReader in)` - deserializes ISO text to datetime.

### `LocationStorage`
Responsibility:
- JSON adapter for reading/writing locations with country references.

Constructors:
- `LocationStorage()` - default file path (`data/locations.json`).
- `LocationStorage(Path dataFilePath)` - explicit file path.

Methods:
- `save(List<Location> locations)` - writes location list.
- `load()` - reads location list.

## Package: `trip`

### `Trip`
Responsibility:
- Aggregate root representing one trip, including activities and expenses.

Constructors:
- `Trip(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime, Country country)`.
- `Trip(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime)`.

Methods:
- `getActivities()` - returns immutable activity list.
- `getExpenses()` - returns immutable trip-level expense list.
- `setExpenses(List<Expense> expenses)` - replaces trip-level expenses.
- `getCountry()` - returns trip country.
- `setCountry(Country country)` - updates trip country.
- `getStartDateTime()` - returns trip start.
- `setStartDateTime(LocalDateTime startDateTime)` - updates start.
- `getEndDateTime()` - returns trip end.
- `setEndDateTime(LocalDateTime endDateTime)` - updates end.
- `addActivity(Activity activity)` - adds activity to trip.
- `deleteActivityById(int id)` - deletes activity by id.
- `deleteActivityByName(String name)` - deletes activity by name.
- `getOverlappingActivities()` - returns activities that overlap each other.
- `getOverlappingAcitivites(LocalDateTime begin, LocalDateTime end)` - returns activities overlapping a window.
- `getOverlappingActivities(LocalDateTime begin, LocalDateTime end)` - alias to overlap-window query.
- `addExpense(Expense expense)` - adds trip-level expense.
- `deleteExpenseById(int id)` - deletes expense by id.
- `deleteExpenseByName(String name)` - deletes expense by name.
- `getExpenseById(int id)` - fetches expense by id.
- `getExpenseByName(String name)` - fetches expense by name.
- `getTotalCost(Expense.Currency currency)` - sums costs for one currency (trip + activities).
- `copy()` - deep copies trip aggregate.
- `toString()` - returns formatted trip summary text.

### `TripManager`
Responsibility:
- Application service orchestrating trip lifecycle, validation, and persistence.

Constructors:
- `TripManager()` - default JSON storage.
- `TripManager(JsonStorage storage)` - explicit storage.
- `TripManager(List<Trip> trips)` - initializes from preloaded trips.

Methods:
- `loadFromFile()` - loads trips without external reference resolution.
- `loadFromFile(CountryRepository countryRepository, LocationRepository locationRepository)` - loads with country/location resolution.
- `loadFromFile(CountryRepository countryRepository, LocationRepository locationRepository, ExpenseRepository expenseRepository)` - loads with full reference resolution.
- `saveToFile()` - persists trips.
- `getTrips()` - returns immutable trip view.
- `addTrip(Trip trip)` - validates and adds trip.
- `updateTrip(int tripId, String name, LocalDateTime startDateTime, LocalDateTime endDateTime, Country country)` - updates existing trip.
- `deleteTripById(int id)` - deletes trip by id.
- `deleteTripByName(String name)` - deletes trip by name.
- `nextAvailableId()` - returns next unused trip id.
- `getTripById(int id)` - fetches trip by id.
- `getTripByName(String name)` - fetches trip by name.
- `getOverlappingTrips()` - returns trips that overlap each other.
- `getOverlappingTrips(LocalDateTime begin, LocalDateTime end)` - returns trips overlapping a window.

## Package: `ui.control`

### `MainWindowControl` (interface)
Responsibility:
- Cross-page control contract exposed by `MainWindow`.

Methods:
- `showHomePage()` - navigate to home page.
- `showTripPage(Trip trip)` - navigate to trip page.
- `showActivityPage(Activity activity, TripPage tripPage)` - navigate to activity page.
- `getAvailableLocations()` - list available locations.
- `promptAddLocation()` - run add-location flow.
- `promptEditLocation(Location location)` - run edit-location flow.
- `promptEditTrip(Trip trip)` - run edit-trip flow.
- `deleteLocationFromUi(Location location, Runnable onDataChanged)` - delete location via UI flow.
- `configureLocationComboForDelete(ComboBox<Location> locationCombo, Runnable onDataChanged)` - wire delete actions on location combo.
- `cleanupExpenseIfOrphaned(int expenseId)` - remove unreferenced expense.
- `refreshHeaderActivitySummary()` - refresh home-header activity snapshot.

### `HomeActivitySnapshotController`
Responsibility:
- Renders ongoing/upcoming activity snapshot cards on the home page.

Constructors:
- `HomeActivitySnapshotController(VBox ongoingActivityContainer, VBox upcomingActivityContainer)`.

Methods:
- `refresh(List<Trip> trips)` - rebuilds snapshot sections from current trips.

### `MainWindowDialogSupport`
Responsibility:
- Shared static utility methods for JavaFX dialog styling, parsing, and form helpers.

Methods:
- `applyDialogTheme(Dialog<?> dialog, String styleClass, String cancelButtonStyleClass)` - applies common stylesheet and style classes.
- `createAddButton(String text)` - returns styled add button.
- `createEditButton(String text)` - returns styled edit button.
- `createDeleteButton(String text)` - returns styled delete button.
- `createResponsiveActionRow(ComboBox<?> combo, Button addButton, Button editButton, Button deleteButton)` - returns responsive action row.
- `chooseImagePath(Dialog<?> dialog, TextField targetField)` - opens file chooser and writes chosen path.
- `createCountryConverter()` - returns combo-box converter for countries.
- `parseTimeOrDefault(String text, LocalTime fallback)` - parses time with fallback.
- `parseOptionalDouble(String text)` - parses optional decimal value.

### `MainWindowLookupCrudController`
Responsibility:
- Country/location CRUD orchestrator used by main-window and form flows.

Constructors:
- `MainWindowLookupCrudController(BorderPane rootPane, TripManager tripManager, CountryRepository countryRepository, LocationRepository locationRepository, ExpenseRepository expenseRepository, Runnable refreshHeaderAction, Consumer<String> errorSink, BiConsumer<String, String> infoSink)`.

Methods:
- `getAvailableCountries()` - returns countries from repository.
- `getAvailableLocations()` - returns locations from repository.
- `promptAddCountry()` - opens add-country dialog.
- `promptEditCountry(Country country)` - opens edit-country dialog.
- `promptAddLocation()` - opens add-location dialog.
- `promptEditLocation(Location location)` - opens edit-location dialog.
- `deleteCountryFromUi(Country country, Runnable onDataChanged)` - deletes country with reference checks.
- `deleteLocationFromUi(Location location, Runnable onDataChanged)` - deletes location with reference checks.
- `configureLocationComboForDelete(ComboBox<Location> locationCombo, Runnable onDataChanged)` - wires location combo context deletes.
- `configureCountryComboForDelete(ComboBox<Country> countryCombo, Runnable onDataChanged)` - wires country combo context deletes.

### `MainWindowReferenceInspector`
Responsibility:
- Read-only reference scanner used before destructive delete operations.

Methods:
- `findCountryReferences(TripManager tripManager, int countryId)` - lists trip references to a country.
- `findLocationReferences(TripManager tripManager, int locationId)` - lists activity references to a location.

### `MainWindowTripCrudController`
Responsibility:
- Trip CRUD/dialog orchestrator, including orphan-expense cleanup after deletions.

Constructors:
- `MainWindowTripCrudController(TripManager tripManager, ExpenseRepository expenseRepository, MainWindowLookupCrudController lookupController, Runnable refreshTripListAction, Runnable refreshHeaderAction, Consumer<String> errorSink)`.

Methods:
- `showAddTripDialog()` - opens add-trip dialog and persists on success.
- `promptEditTrip(Trip trip)` - opens edit-trip dialog.
- `deleteTripFromUi(Trip trip)` - deletes trip and performs cleanup.
- `cleanupExpenseIfOrphaned(int expenseId)` - removes expense if no remaining references.

## Package: `ui`

### `MainWindow`
Responsibility:
- Top-level JavaFX controller that wires services/repositories and routes page navigation.

Constructors:
- `MainWindow()` - default constructor (FXML controller).

Methods:
- `initialize()` - loads repositories/state and initializes sub-controllers.
- `showHomePage()` - displays home view.
- `showTripPage(Trip trip)` - displays selected trip view.
- `showActivityPage(Activity activity, TripPage tripPage)` - displays selected activity view.
- `getAvailableCountries()` - passthrough to lookup controller.
- `getAvailableLocations()` - passthrough to lookup controller.
- `promptAddCountry()` - opens add-country flow.
- `promptEditCountry(Country country)` - opens edit-country flow.
- `promptAddLocation()` - opens add-location flow.
- `promptEditLocation(Location location)` - opens edit-location flow.
- `promptEditTrip(Trip trip)` - opens edit-trip flow.
- `deleteTripFromUi(Trip trip)` - deletes trip via UI flow.
- `deleteCountryFromUi(Country country, Runnable onDataChanged)` - deletes country via UI flow.
- `deleteLocationFromUi(Location location, Runnable onDataChanged)` - deletes location via UI flow.
- `cleanupExpenseIfOrphaned(int expenseId)` - removes orphaned expense.
- `configureLocationComboForDelete(ComboBox<Location> locationCombo, Runnable onDataChanged)` - wires location combo delete behavior.
- `refreshHeaderActivitySummary()` - refreshes home snapshot widgets.
- `showTripGuide()` - shows trip page help dialog.
- `showActivityGuide()` - shows activity page help dialog.

### `TripPage`
Responsibility:
- JavaFX controller for trip-centric timeline, activity, and expense views.

Constructors:
- `TripPage()` - default constructor (FXML controller).

Methods:
- `setTrip(Trip trip)` - binds page to trip and refreshes views.
- `setMainWindowControl(MainWindowControl mainWindowControl)` - injects cross-page control contract.
- `setTripManager(TripManager tripManager)` - injects persistence service.
- `setExpenseRepository(ExpenseRepository expenseRepository)` - injects expense repository.
- `showTripPage()` - navigates back to current trip page from child views.
- `getTrip()` - returns currently bound trip.

### `ActivityPage`
Responsibility:
- JavaFX controller for activity detail and activity-level expense management.

Constructors:
- `ActivityPage()` - default constructor (FXML controller).

Methods:
- `setActivity(Activity activity)` - binds page to selected activity.
- `setTripPage(TripPage tripPage)` - injects parent trip page for navigation.
- `setTripManager(TripManager tripManager)` - injects persistence service.
- `setMainWindowControl(MainWindowControl mainWindowControl)` - injects cross-page control contract.
- `setExpenseRepository(ExpenseRepository expenseRepository)` - injects expense repository.
- `getTripPage()` - returns bound parent trip page.
