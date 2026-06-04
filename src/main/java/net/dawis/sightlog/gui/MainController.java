package net.dawis.sightlog.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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

import java.time.LocalDate;
import java.util.List;

public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    // Top Action Bar
    @FXML private Button btnAddMedia;
    @FXML private Button btnAddPart;
    @FXML private Button btnRefresh;

    // Left Panel: Media Overview Table
    @FXML private TableView<Media> tblMediaOverview;
    @FXML private TableColumn<Media, Long> colMediaId;
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

        // Wire up the refresh button functionality
        btnRefresh.setOnAction(event -> refreshData());

        // Initial Data Load on Startup
        refreshData();
    }

    /**
     * Maps plain fields and dynamically calculates aggregates for the Left Overview Table
     * mirroring the logic of the database's `media_overview` view.
     */
    private void setupOverviewTableColumns() {
        colMediaId.setCellValueFactory(new PropertyValueFactory<>("id"));
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

}