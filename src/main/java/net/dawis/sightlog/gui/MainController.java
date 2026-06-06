package net.dawis.sightlog.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import net.dawis.sightlog.datahandling.DatabaseSession;
import net.dawis.sightlog.datahandling.UserSession;
import net.dawis.sightlog.entities.Media;
import net.dawis.sightlog.entities.MediaPart;
import net.dawis.sightlog.entities.Status;
import net.dawis.sightlog.entities.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class MainController {
    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    // Top Action Bar
    @FXML private Button btnAddMedia, btnAddPart, btnRefresh;

    // Left Panel: Media Overview Table
    @FXML private TableView<Media> tblMediaOverview;
    @FXML private TableColumn<Media, String> colMediaTitle;
    @FXML private TableColumn<Media, String> colMediaType;
    @FXML private TableColumn<Media, String> colMediaCreator;
    @FXML private TableColumn<Media, String> colMediaStudio;
    @FXML private TableColumn<Media, Integer> colMediaPartsCount;
    @FXML private TableColumn<Media, String> colMediaStatus;
    @FXML private TableColumn<Media, Double> colMediaAvgRating;

    // Right Panel: Media Parts Table
    @FXML private TableView<MediaPart> tblMediaParts;
    @FXML private TableColumn<MediaPart, Integer> colPartNumber;
    @FXML private TableColumn<MediaPart, String> colPartTitle;
    @FXML private TableColumn<MediaPart, Integer> colPartYear;
    @FXML private TableColumn<MediaPart, String> colPartStatus;
    @FXML private TableColumn<MediaPart, Double> colPartRating;
    @FXML private TableColumn<MediaPart, LocalDate> colPartStarted;
    @FXML private TableColumn<MediaPart, LocalDate> colPartFinished;
    @FXML private TableColumn<MediaPart, String> colPartNotes;

    // Bottom Status Bar
    @FXML private Label lblStatusMessage;

    private final ObservableList<Media> mediaOverviewList = FXCollections.observableArrayList();
    private final ObservableList<MediaPart> mediaPartsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        LOG.info("Initializing MainController...");

        setupOverviewTableColumns();
        setupPartsTableColumns();

        // Bind the lists to the UI tables
        tblMediaOverview.setItems(mediaOverviewList);
        tblMediaParts.setItems(mediaPartsList);

        // Master-Detail selection listener: Update right table when left row is clicked
        tblMediaOverview.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            mediaPartsList.clear();
            if (newSelection != null && newSelection.getParts() != null) {
                mediaPartsList.addAll(newSelection.getParts());
                LOG.debug("Loaded {} parts for media: {}", newSelection.getParts().size(), newSelection.getTitle());
            }
        });

        tblMediaOverview.setOnMouseClicked(event -> {
            Node target = (Node) event.getTarget();
            while (target != null && target != tblMediaOverview) {
                if (target instanceof TableRow && !((TableRow<?>) target).isEmpty()) {
                    return; // Hit a valid row, preserve selection
                }
                target = target.getParent();
            }
            tblMediaOverview.getSelectionModel().clearSelection();
            mediaPartsList.clear();
        });

        tblMediaParts.setOnMouseClicked(event -> {
            Node target = (Node) event.getTarget();
            while (target != null && target != tblMediaParts) {
                if (target instanceof TableRow && !((TableRow<?>) target).isEmpty()) {
                    return; // Hit a valid row, preserve selection
                }
                target = target.getParent();
            }
            tblMediaParts.getSelectionModel().clearSelection();
        });

        // Configure Double-Click Row Interaction on the Parts Table View
        tblMediaParts.setRowFactory(tv -> {
            TableRow<MediaPart> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    MediaPart selectedPart = row.getItem();
                    handleEditMediaPart(selectedPart);
                }
            });
            return row;
        });

        // Wire up button actions
        btnRefresh.setOnAction(event -> refreshData());

        // Explicit action routing based on button context
        btnAddMedia.setOnAction(event -> handleOpenDialogAction(true));
        btnAddPart.setOnAction(event -> handleOpenDialogAction(false));

        // Initial Data Load on Startup
        refreshData();
    }

    /**
     * Maps plain fields and dynamically calculates aggregates for the Left Overview Table
     * mirroring the logic of the database's `media_overview` view.
     */
    private void setupOverviewTableColumns() {
        colMediaTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colMediaCreator.setCellValueFactory(new PropertyValueFactory<>("creator"));
        colMediaStudio.setCellValueFactory(new PropertyValueFactory<>("studio"));

        colMediaType.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getMediaType() != null ? cellData.getValue().getMediaType().name() : ""));

        // Aggregate: Part Count
        colMediaPartsCount.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getParts() != null ? cellData.getValue().getParts().size() : 0).asObject());

        // Aggregate View Logic: Status Calculation
        colMediaStatus.setCellValueFactory(cellData -> {
            List<MediaPart> parts = cellData.getValue().getParts();
            if (parts == null || parts.isEmpty()) {
                return new SimpleStringProperty(Status.PLANNING.name());
            }
            if (parts.stream().anyMatch(p -> p.getStatus() == Status.IN_PROGRESS)) {
                return new SimpleStringProperty(Status.IN_PROGRESS.name());
            }
            if (parts.stream().anyMatch(p -> p.getStatus() == Status.DROPPED)) {
                return new SimpleStringProperty(Status.DROPPED.name());
            }
            if (parts.stream().allMatch(p -> p.getStatus() == Status.FINISHED)) {
                return new SimpleStringProperty(Status.FINISHED.name());
            }
            return new SimpleStringProperty(Status.PLANNING.name());
        });

        // Aggregate View Logic: Average Rating Calculation
        colMediaAvgRating.setCellValueFactory(cellData -> {
            List<MediaPart> parts = cellData.getValue().getParts();
            if (parts == null || parts.isEmpty()) {
                return new SimpleObjectProperty<>(null);
            }
            double avg = parts.stream()
                    .filter(p -> p.getRating() != null)
                    .mapToDouble(MediaPart::getRating)
                    .average()
                    .orElse(-1.0);

            if (avg == -1.0) return new SimpleObjectProperty<>(null); // Matches SQL view NULL output
            return new SimpleDoubleProperty(Math.round(avg * 100.0) / 100.0).asObject();
        });
    }

    /**
     * Maps direct tracking properties to the Right Parts Table.
     */
    private void setupPartsTableColumns() {
        colPartNumber.setCellValueFactory(new PropertyValueFactory<>("partNumber"));
        colPartTitle.setCellValueFactory(new PropertyValueFactory<>("partTitle"));
        colPartYear.setCellValueFactory(new PropertyValueFactory<>("releaseYear"));
        colPartRating.setCellValueFactory(new PropertyValueFactory<>("rating"));
        colPartStarted.setCellValueFactory(new PropertyValueFactory<>("startedAt"));
        colPartFinished.setCellValueFactory(new PropertyValueFactory<>("finishedAt"));
        colPartNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));

        colPartStatus.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus() != null ? cellData.getValue().getStatus().name() : ""));
    }

    /**
     * Queries the database via Hibernate and updates the UI components.
     */
    private void refreshData() {
        LOG.info("Refreshing media library data from database...");
        lblStatusMessage.setText("Refreshing data...");

        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            LOG.error("No active user session found");
            lblStatusMessage.setText("No active user session found");
        }

        try (Session session = DatabaseSession.open()) {
            Transaction tx = session.beginTransaction();

            session.setDefaultReadOnly(true);

            List<Media> mediaList = session.createQuery(
                    "SELECT DISTINCT m FROM Media m " +
                            "LEFT JOIN FETCH m.parts " +
                            "WHERE m.user.id = :currentUserId", Media.class
            ).setParameter("currentUserId", currentUser.getId())
            .getResultList();

            tx.commit();

            // UI Updates
            mediaOverviewList.setAll(mediaList);
            tblMediaOverview.getSelectionModel().clearSelection();
            mediaPartsList.clear();

            lblStatusMessage.setText("Data loaded successfully. Total Titles: " + mediaList.size());
            LOG.info("Successfully fetched {} media roots.", mediaList.size());
        } catch (Exception e) {
            LOG.error("Failed to load data from database.", e);
            lblStatusMessage.setText("Error loading data from database!");
        }
    }

    /**
     * Evaluates active UI selection models to open either a fresh Media form
     * or append a new child Part to an existing tracked media profile.
     */
    private void handleOpenDialogAction(boolean isBrandNewMedia) {
        Media selectedMedia = tblMediaOverview.getSelectionModel().getSelectedItem();

        // Guard Clause: Prevent exceptions if user attempts to add a part with no root selection active
        if (!isBrandNewMedia && selectedMedia == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Selection Required");
            alert.setHeaderText("No Media Title Selected");
            alert.setContentText("Please select a valid Media entry from the left table before attempting to append a new tracking segment/part.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/gui/media_dialog.fxml"));
            DialogPane dialogPane = loader.load();
            MediaDialogController dialogController = loader.getController();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);

            if (isBrandNewMedia) {
                dialog.setTitle("Add New Media Entry");
                dialogController.setContext(null);
            } else {
                dialog.setTitle("Add Part to: " + selectedMedia.getTitle());
                dialogController.setContext(selectedMedia);
            }

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (dialogController.isValidInput()) {
                    saveNewFormEntry(dialogController, isBrandNewMedia ? null : selectedMedia);
                    refreshData(); // Synchronize live DB states back to presentation structures
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to accurately display input wizard dialog frame", e);
        }
    }

    /**
     * Implements a transactional workflow mapping form inputs to relational backend database entities.
     */
    private void saveNewFormEntry(MediaDialogController controller, Media selectedMedia) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            LOG.error("Session execution terminated: No active context user authenticated");
            return;
        }

        try (Session session = DatabaseSession.open()) {
            Transaction tx = session.beginTransaction();

            if (selectedMedia == null) {
                // Case A: Create completely new media tree parent alongside its initial base part element
                Media newMedia = controller.getMediaInput();
                newMedia.setUser(currentUser);

                MediaPart initialPart = controller.getMediaPartInput();
                initialPart.setMedia(newMedia);
                newMedia.getParts().add(initialPart);

                session.persist(newMedia);
                LOG.info("Successfully registered core media title profile: {}", newMedia.getTitle());
            } else {
                // Case B: Append tracking part segment to an existing parent node record
                MediaPart newPart = controller.getMediaPartInput();

                // Re-fetch object scope inside active session boundary to eliminate detached state conflicts
                Media managedParent = session.find(Media.class, selectedMedia.getId());
                newPart.setMedia(managedParent);
                managedParent.getParts().add(newPart);

                session.persist(newPart);
                LOG.info("Appended part collection sequence index {} to parent ID: {}", newPart.getPartNumber(), managedParent.getId());
            }

            tx.commit();
            lblStatusMessage.setText("Log database entry processed successfully!");
        } catch (Exception e) {
            LOG.error("Critical error encountered writing new logging configurations to database layer", e);
            lblStatusMessage.setText("Database write failure! Integrity constraint violated.");
        }
    }

    private void handleEditMediaPart(MediaPart selectedPart) {
        if (selectedPart == null) return;

        try {
            // 1. Load the existing layout dialog structure
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/media_dialog.fxml"));
            DialogPane dialogPane = loader.load();
            MediaDialogController controller = loader.getController();

            // 2. Inject active entity metadata properties directly into UI form
            controller.populateFields(selectedPart);

            // 3. Build a modal presentation window context
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Edit Tracking Partition - " + selectedPart.getMedia().getTitle());

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {

                try (Session session = DatabaseSession.open()) {
                    Transaction tx = session.beginTransaction();

                    // Load attached references directly inside active persistence engine framework
                    MediaPart managedPart = session.find(MediaPart.class, selectedPart.getId());
                    Media managedMedia = managedPart.getMedia();

                    // Extract modified payload values from screen fields
                    Media inputMedia = controller.getMediaInput();
                    MediaPart inputPart = controller.getMediaPartInput();

                    // Synchronize parent core profile parameters
                    managedMedia.setTitle(inputMedia.getTitle());
                    managedMedia.setMediaType(inputMedia.getMediaType());
                    managedMedia.setCreator(inputMedia.getCreator());
                    managedMedia.setStudio(inputMedia.getStudio());
                    managedMedia.setDescription(inputMedia.getDescription());

                    // Synchronize live iteration logging coordinates
                    managedPart.setPartNumber(inputPart.getPartNumber());
                    managedPart.setPartTitle(inputPart.getPartTitle());
                    managedPart.setReleaseYear(inputPart.getReleaseYear());

                    // CRITICAL FOR TRIGGER: Updating status from FINISHED -> IN_PROGRESS here
                    // tells PostgreSQL to run its archive routines right before overwriting these values.
                    managedPart.setStatus(inputPart.getStatus());
                    managedPart.setRating(inputPart.getRating());
                    managedPart.setStartedAt(inputPart.getStartedAt());
                    managedPart.setFinishedAt(inputPart.getFinishedAt());
                    managedPart.setNotes(inputPart.getNotes());

                    // Persist live operational values
                    session.merge(managedPart);

                    // Commit Transaction -> Database triggers execute right here!
                    tx.commit();

                    // Flush and clear cache to maintain synchronization with server automation states
                    session.clear();

                    LOG.info("Tracking transaction committed. Trigger completed downstream log archiving successfully.");
                    lblStatusMessage.setText("Log entry variations updated successfully.");

                    // Re-hydrate application interfaces with updated tracking configurations
                    refreshData();

                } catch (Exception ex) {
                    LOG.error("Critical issue committing live update configuration profile", ex);
                    lblStatusMessage.setText("Database update failed! Check data integrity restrictions.");
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to present layout component sequence via dialog scene engine", e);
            lblStatusMessage.setText("Error: Failed to display editing modal window!");
        }
    }
}