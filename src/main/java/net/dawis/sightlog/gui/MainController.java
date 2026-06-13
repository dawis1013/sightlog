package net.dawis.sightlog.gui;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.dawis.sightlog.datahandling.DatabaseSession;
import net.dawis.sightlog.datahandling.PasswordUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Main controller for the application's dashboard.
 * Manages the media library display, filtering, and interaction with media/parts.
 */
public class MainController {
    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    // Top Action Bar
    @FXML private Button btnAddMedia, btnAddPart, btnRefresh, btnDeleteMedia, btnAccount;

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

    /**
     * Initializes the controller. Sets up tables, listeners, and performs initial data load.
     */
    @FXML
    public void initialize() {
        LOG.info("Initializing MainController...");

        setupOverviewTableColumns();
        setupPartsTableColumns();

        // Bind the lists to the UI tables
        tblMediaOverview.setItems(mediaOverviewList);
        tblMediaParts.setItems(mediaPartsList);

        setupTableListeners();
        setupButtonActions();

        // Initial Data Load on Startup
        refreshData();
    }

    /**
     * Configures selection and click listeners for the tables.
     */
    private void setupTableListeners() {
        // Master-Detail selection listener: Update right table when left row is clicked
        tblMediaOverview.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            mediaPartsList.clear();
            if (newSelection != null && newSelection.getParts() != null) {
                mediaPartsList.addAll(newSelection.getParts());
                LOG.debug("Loaded {} parts for media: {}", newSelection.getParts().size(), newSelection.getTitle());
            }
        });

        // Clear selection when clicking empty space in Overview table
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

        // Clear selection when clicking empty space in Parts table
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
    }

    /**
     * Configures actions for the top bar buttons.
     */
    private void setupButtonActions() {
        btnRefresh.setOnAction(event -> refreshData());

        // Explicit action routing based on button context
        btnAddMedia.setOnAction(event -> handleOpenDialogAction(true));
        btnAddPart.setOnAction(event -> handleOpenDialogAction(false));

        btnDeleteMedia.setOnAction(event -> handleDeleteAction());

        btnAccount.setOnAction(event -> showAccountSettings());
    }

    /**
     * Maps plain fields and dynamically calculates aggregates for the Left Overview Table.
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

            if (avg == -1.0) return new SimpleObjectProperty<>(null); 
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
            LOG.error("No active user session found during refresh.");
            lblStatusMessage.setText("No active user session found");
            return;
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
            LOG.error("Failed to load data from database: {}", e.getMessage());
            lblStatusMessage.setText("Error loading data from database!");
        }
    }

    /**
     * Opens the add/edit dialog for Media or MediaPart.
     * @param isBrandNewMedia true if adding a new Media, false if adding a part to selected Media.
     */
    private void handleOpenDialogAction(boolean isBrandNewMedia) {
        Media selectedMedia = tblMediaOverview.getSelectionModel().getSelectedItem();

        // Guard Clause: Prevent exceptions if user attempts to add a part with no root selection active
        if (!isBrandNewMedia && selectedMedia == null) {
            LOG.warn("Add Part attempted without selecting a Media entry.");
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
            LOG.error("Failed to display input wizard dialog: {}", e.getMessage());
            lblStatusMessage.setText("Error: Failed to display input dialog.");
        }
    }

    /**
     * Persists a new Media or MediaPart entry to the database.
     * @param controller The dialog controller containing the input data.
     * @param selectedMedia The parent Media if adding a part, null if adding new Media.
     */
    private void saveNewFormEntry(MediaDialogController controller, Media selectedMedia) {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            LOG.error("Session execution terminated: No active context user authenticated");
            lblStatusMessage.setText("Error: No active user session.");
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

                // --- MANUAL CONCURRENCY CONTROL: Verify root version against initial selection ---
                if (managedParent.getVersion() != selectedMedia.getVersion()) {
                    throw new jakarta.persistence.OptimisticLockException("The Media title root has been modified by another session. Update aborted to prevent data corruption.");
                }

                newPart.setMedia(managedParent);
                managedParent.getParts().add(newPart);

                session.persist(newPart);
                LOG.info("Appended part collection sequence index {} to parent ID: {}", newPart.getPartNumber(), managedParent.getId());
            }

            tx.commit();
            lblStatusMessage.setText("Log database entry processed successfully!");
        } catch (jakarta.persistence.OptimisticLockException ole) {
            LOG.warn("Concurrency violation during new part insertion: {}", ole.getMessage());
            showConcurrencyError();
        } catch (Exception e) {
            LOG.error("Critical error writing to database: {}", e.getMessage());
            lblStatusMessage.setText("Database write failure! Data integrity error.");
        }
    }

    /**
     * Handles editing an existing MediaPart.
     * @param selectedPart The MediaPart to edit.
     */
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

                    // --- MANUAL CONCURRENCY CONTROL: Verify current DB version against initial memory snapshot ---
                    if (managedPart.getVersion() != selectedPart.getVersion()) {
                        throw new jakarta.persistence.OptimisticLockException("This tracking part has been modified by another session.");
                    }
                    if (managedMedia.getVersion() != selectedPart.getMedia().getVersion()) {
                        throw new jakarta.persistence.OptimisticLockException("The parent Media title root has been modified by another session.");
                    }

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

                    managedPart.setStatus(inputPart.getStatus());
                    managedPart.setRating(inputPart.getRating());
                    managedPart.setStartedAt(inputPart.getStartedAt());
                    managedPart.setFinishedAt(inputPart.getFinishedAt());
                    managedPart.setNotes(inputPart.getNotes());

                    // Persist live operational values
                    session.merge(managedPart);

                    tx.commit();
                    session.clear();

                    LOG.info("Tracking transaction committed for part ID: {}", managedPart.getId());
                    lblStatusMessage.setText("Log entry variations updated successfully.");

                    refreshData();

                } catch (jakarta.persistence.OptimisticLockException ole) {
                    LOG.warn("Concurrency conflict detected during update: {}", ole.getMessage());
                    showConcurrencyError();
                    refreshData();
                } catch (Exception ex) {
                    LOG.error("Critical issue committing live update: {}", ex.getMessage());
                    lblStatusMessage.setText("Database update failed! Data integrity error.");
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to display editing dialog: {}", e.getMessage());
            lblStatusMessage.setText("Error: Failed to display editing modal window!");
        }
    }

    /**
     * Displays a warning alert when a database concurrency conflict (optimistic lock failure) is detected.
     */
    private void showConcurrencyError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Concurrency Conflict Encountered");
        alert.setHeaderText("Database record mismatch: Data Outdated");
        alert.setContentText("The information you are trying to update has been modified by another session since you loaded it. \n\n"
                + "Your changes have been aborted to prevent accidental data overwriting. The view will now refresh to show the latest data.");
        alert.showAndWait();
    }

    /**
     * Orchestrates the deletion framework strategy based on explicit user table selection coordinates.
     */
    private void handleDeleteAction() {
        MediaPart selectedPart = tblMediaParts.getSelectionModel().getSelectedItem();
        Media selectedMedia = tblMediaOverview.getSelectionModel().getSelectedItem();

        if (selectedPart != null) {
            LOG.info("Delete trigger invoked. Found highlighted active track part index context ID: {}.", selectedPart.getId());

            // Step 1: Initialize Choice Window Scope Alert Framework Context Definition
            Alert scopeAlert = new Alert(Alert.AlertType.CONFIRMATION);
            scopeAlert.setTitle("Selection Purge Parameters");
            scopeAlert.setHeaderText("Choose Deletion Strategy Boundary Range");
            scopeAlert.setContentText("An entry item segment row is currently highlighted on the partition tracker. Would you like to target this single subset log, or delete the entire root entity profile?");

            ButtonType btnDeleteOnlyPart = new ButtonType("Purge Selected Part Only");
            ButtonType btnDeleteWholeMedia = new ButtonType("Purge Entire Title Root");
            ButtonType btnCancelAction = new ButtonType("Cancel Operation", ButtonBar.ButtonData.CANCEL_CLOSE);

            scopeAlert.getButtonTypes().setAll(btnDeleteOnlyPart, btnDeleteWholeMedia, btnCancelAction);

            Optional<ButtonType> scopeResponse = scopeAlert.showAndWait();
            if (scopeResponse.isEmpty() || scopeResponse.get() == btnCancelAction) {
                LOG.info("Scope selection aborted by developer/user boundary interface interaction action.");
                return;
            }

            // Step 2: Route operational control paths downstream to dedicated explicit confirmation alert loops
            if (scopeResponse.get() == btnDeleteOnlyPart) {
                processPartDeletion(selectedPart);
            } else if (scopeResponse.get() == btnDeleteWholeMedia) {
                processMediaDeletion(selectedPart.getMedia());
            }

        } else if (selectedMedia != null) {
            LOG.info("Delete trigger invoked. No subset parts found, shifting scope default to parent root entity ID: {}.", selectedMedia.getId());
            processMediaDeletion(selectedMedia);
        } else {
            LOG.warn("Delete operational callback invoked, but active model evaluation coordinates returned no selected reference context.");
            lblStatusMessage.setText("Warning: Please select a valid target inside tracking charts to run standard delete operations.");
        }
    }

    /**
     * Executes explicit confirmation popup validation loops and purges a single track partition entity item.
     */
    private void processPartDeletion(MediaPart targetPart) {
        Alert confirmAlert = new Alert(Alert.AlertType.WARNING);
        confirmAlert.setTitle("Verify Destruction Event Bounds");
        confirmAlert.setHeaderText("Confirm Destruction: Tracking Segment Row Subset");
        confirmAlert.setContentText("Are you entirely sure you want to drop tracking subset entry part number "
                + targetPart.getPartNumber() + " ('" + (targetPart.getPartTitle() != null ? targetPart.getPartTitle() : "Untitled")
                + "')?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            LOG.info("Destructive signature verified. Committing transaction drop request for segment element index ID: {}", targetPart.getId());

            try (Session session = DatabaseSession.open()) {
                Transaction tx = session.beginTransaction();

                MediaPart managedPart = session.find(MediaPart.class, targetPart.getId());
                if (managedPart != null) {
                    Media parentMedia = managedPart.getMedia();
                    if (parentMedia != null) {
                        parentMedia.getParts().remove(managedPart);
                    }
                    session.remove(managedPart);
                }

                tx.commit();
                lblStatusMessage.setText("Tracking subset item dropped from database.");

                tblMediaParts.getSelectionModel().clearSelection();
                refreshData();
            } catch (Exception ex) {
                LOG.error("Failed to commit drop sequence parameters inside active relational tree tracking records", ex);
                lblStatusMessage.setText("Error: Transaction integrity aborted single segment dropping routine.");
            }
        }
    }

    /**
     * Executes explicit confirmation validation loops and drops the root parent entity along with all cascades.
     */
    private void processMediaDeletion(Media targetMedia) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Verify Structural Failure Risks");
        confirmAlert.setHeaderText("CRITICAL ACTION: Drop Title Base Node Frame Root Completely");
        confirmAlert.setContentText("Are you entirely sure you want to permanently drop the core tracking root entry '"
                + targetMedia.getTitle() + "' along with all subordinate tracking variations and logged background internal metrics?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            LOG.info("Destructive master root authorization sequence cleared. Drop transactional parameters initiated for Parent ID: {}", targetMedia.getId());

            try (Session session = DatabaseSession.open()) {
                Transaction tx = session.beginTransaction();

                Media managedMedia = session.find(Media.class, targetMedia.getId());
                if (managedMedia != null) {
                    session.remove(managedMedia);
                }

                tx.commit();
                lblStatusMessage.setText("Master core title framework drop successfully compiled.");

                tblMediaOverview.getSelectionModel().clearSelection();
                tblMediaParts.getSelectionModel().clearSelection();
                refreshData();
            } catch (Exception ex) {
                LOG.error("Failed to wipe relational node reference tree coordinates out of targeted data clusters", ex);
                lblStatusMessage.setText("Error: Failed to process clean context removal parameters.");
            }
        }
    }

    /**
     * Spawns an interactive Account Settings view modal container to review user profile information
     * and route credential changes, logouts, or permanent database profile deletions.
     */
    private void showAccountSettings() {
        LOG.info("Account properties management interface requested.");
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            LOG.error("Account management request rejected: Missing authenticated active user session context.");
            lblStatusMessage.setText("Error: Session expired. Please log in again.");
            return;
        }

        // Create programmatic layout containers to isolate dialogue constraints cleanly
        Dialog<Void> accountDialog = new Dialog<>();
        accountDialog.setTitle("Account Settings");
        accountDialog.setHeaderText("Manage Profile Credentials & Active Sessions");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 40, 20, 20));

        grid.add(new Label("Active User:"), 0, 0);
        Label lblUser = new Label(currentUser.getUsername());
        lblUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #6366f1;");
        grid.add(lblUser, 1, 0);

        grid.add(new Label("Created On:"), 0, 1);
        String createdStr = "N/A";
        if (currentUser.getCreatedAt() != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                createdStr = currentUser.getCreatedAt().format(formatter);
            } catch (Exception e) {
                createdStr = currentUser.getCreatedAt().toString();
            }
        }
        grid.add(new Label(createdStr), 1, 1);

        Button btnChangePassword = new Button("🔑 Change Password");
        Button btnLogoutAction = new Button("🚪 Log Out Session");
        Button btnDeleteAccount = new Button("⚠️ Delete Account Permanently");

        btnChangePassword.setMaxWidth(Double.MAX_VALUE);
        btnLogoutAction.setMaxWidth(Double.MAX_VALUE);
        btnDeleteAccount.setMaxWidth(Double.MAX_VALUE);
        btnDeleteAccount.setStyle("-fx-text-fill: #ef4444; -fx-border-color: #ef4444;");

        VBox buttonContainer = new VBox(10, btnChangePassword, btnLogoutAction, btnDeleteAccount);
        buttonContainer.setPadding(new Insets(15, 0, 0, 0));
        grid.add(buttonContainer, 0, 2, 2, 1);

        accountDialog.getDialogPane().setContent(grid);
        accountDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Sub-Action A: Passphrase Correction Matrix Sub-dialog
        btnChangePassword.setOnAction(e -> {
            LOG.debug("Invoking credentials adjustment sub-modal interface.");
            Dialog<ButtonType> pwDialog = new Dialog<>();
            pwDialog.setTitle("Change Password");
            pwDialog.setHeaderText("Modify Secure Account Access Passphrase");

            GridPane pwGrid = new GridPane();
            pwGrid.setHgap(10);
            pwGrid.setVgap(10);
            pwGrid.setPadding(new Insets(20, 30, 20, 20));

            PasswordField txtCurrentPassword = new PasswordField();
            txtCurrentPassword.setPromptText("Enter current password...");
            PasswordField txtNewPassword = new PasswordField();
            txtNewPassword.setPromptText("Enter new secure password...");

            pwGrid.add(new Label("Current Password:"), 0, 0);
            pwGrid.add(txtCurrentPassword, 1, 0);
            pwGrid.add(new Label("New Password:"), 0, 1);
            pwGrid.add(txtNewPassword, 1, 1);

            pwDialog.getDialogPane().setContent(pwGrid);
            pwDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> pwResult = pwDialog.showAndWait();
            if (pwResult.isPresent() && pwResult.get() == ButtonType.OK) {
                String currentPw = txtCurrentPassword.getText() != null ? txtCurrentPassword.getText().trim() : "";
                String newPw = txtNewPassword.getText() != null ? txtNewPassword.getText().trim() : "";

                if (currentPw.isEmpty() || newPw.isEmpty()) {
                    Alert validationAlert = new Alert(Alert.AlertType.ERROR, "Password inputs cannot be empty or pure whitespace spaces.", ButtonType.OK);
                    validationAlert.showAndWait();
                    return;
                }

                try (Session session = DatabaseSession.open()) {
                    Transaction tx = session.beginTransaction();
                    User managedUser = session.find(User.class, currentUser.getId());

                    if (managedUser == null) {
                        LOG.error("Database resolution mismatch: Logged in profile ID {} missing from tracking entries.", currentUser.getId());
                        return;
                    }

                    // --- MANUAL CONCURRENCY CONTROL: Verify profile version against session snapshot ---
                    if (managedUser.getVersion() != currentUser.getVersion()) {
                        throw new jakarta.persistence.OptimisticLockException("User profile metadata is outdated.");
                    }

                    // Security Matching verification context
                    if (!PasswordUtil.checkPassword(currentPw, managedUser.getPwdHash())) {
                        LOG.warn("Password change unauthorized: Current raw credentials mismatch for username '{}'.", managedUser.getUsername());
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR, "Authentication failed: The current password entered is incorrect.", ButtonType.OK);
                        errorAlert.showAndWait();
                        return;
                    }

                    managedUser.setPwdHash(PasswordUtil.hashPassword(newPw));
                    session.merge(managedUser);
                    tx.commit();

                    LOG.info("Passphrase string successfully committed for User ID {}. Invalidating session state structures.", managedUser.getId());
                    accountDialog.close();
                    redirectToLogin();
                } catch (jakarta.persistence.OptimisticLockException ole) {
                    LOG.warn("Concurrency violation during password update: {}", ole.getMessage());
                    Alert lockAlert = new Alert(Alert.AlertType.ERROR, "Account Update Failed: Your session data is outdated. Please log out and log back in to synchronize your profile.", ButtonType.OK);
                    lockAlert.showAndWait();
                } catch (Exception ex) {
                    LOG.error("Hibernate error encountered committing password changes to persistence layers.", ex);
                    lblStatusMessage.setText("Error: Database layer failed to save password change properties.");
                }
            }
        });

        // Sub-Action B: Session Extraction Action Route
        btnLogoutAction.setOnAction(e -> {
            LOG.info("User requested explicit application session log out.");
            accountDialog.close();
            redirectToLogin();
        });

        // Sub-Action C: Permanent Profile Destruction & Cascade Purging Routine Loop
        btnDeleteAccount.setOnAction(e -> {
            LOG.warn("Critical user account destruction script requested for active login session ID: {}", currentUser.getId());
            Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDelete.setTitle("Verify Structural Failure Risks");
            confirmDelete.setHeaderText("CRITICAL INTERFACES COMMAND: Permanently Drop Profile Account Framework");
            confirmDelete.setContentText("Are you entirely sure you want to permanently delete your user account profile? "
                    + "This will instantly trigger cascaded clean removal iterations across all your tracked Media logs, subset partition layers, and history logs from database clusters. "
                    + "This destructive operation cannot be reversed.");

            Optional<ButtonType> deleteResult = confirmDelete.showAndWait();
            if (deleteResult.isPresent() && deleteResult.get() == ButtonType.OK) {
                LOG.info("Destructive authorization keys parsed successfully. Executing profile delete routines.");

                try (Session session = DatabaseSession.open()) {
                    Transaction tx = session.beginTransaction();

                    // CRITICAL COMPLIANCE FIX: Iteratively pull and delete media objects to ensure Hibernate triggers Cascades on parts.
                    List<Media> userMediaRoots = session.createQuery(
                                    "FROM Media m WHERE m.user.id = :userId", Media.class)
                            .setParameter("userId", currentUser.getId())
                            .getResultList();

                    LOG.debug("Purging {} owned media tracking trees linked to account context.", userMediaRoots.size());
                    for (Media mediaRoot : userMediaRoots) {
                        session.remove(mediaRoot);
                    }

                    User managedUser = session.find(User.class, currentUser.getId());
                    if (managedUser != null) {
                        session.remove(managedUser);
                    }

                    tx.commit();
                    LOG.info("Account profile and associated metadata clusters successfully deleted from system records.");

                    accountDialog.close();
                    redirectToLogin();
                } catch (Exception ex) {
                    LOG.error("Critical issue handling atomic database user profile purging sequences.", ex);
                    Alert crashAlert = new Alert(Alert.AlertType.ERROR, "System Error: Failed to cleanly drop account profiles due to database constraint boundaries.", ButtonType.OK);
                    crashAlert.showAndWait();
                }
            }
        });

        accountDialog.showAndWait();
    }

    /**
     * Executes clean container window invalidation and drops state elements before routing the frame back to the Login display graph.
     */
    private void redirectToLogin() {
        try {
            UserSession.logout();
            Stage stage = (Stage) btnAccount.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/login_ui.fxml"));
            Scene scene = new Scene(loader.load());
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setTitle("Login");
            stage.show();
            LOG.info("Successfully routed active session back to login screen context view boundary.");
        } catch (IOException e) {
            LOG.error("Failed to redirect window scope to login page FXML layout structure.", e);
            lblStatusMessage.setText("Error: Redirection to login screen failed.");
        }
    }
}