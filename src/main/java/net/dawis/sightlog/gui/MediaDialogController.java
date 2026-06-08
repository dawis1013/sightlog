package net.dawis.sightlog.gui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import net.dawis.sightlog.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Controller for the Media Dialog.
 * Handles adding and editing media titles and their parts.
 */
public class MediaDialogController {
    private static final Logger LOG = LoggerFactory.getLogger(MediaDialogController.class);

    @FXML private VBox mediaSection;
    @FXML private TextField txtMediaTitle, txtMediaCreator, txtMediaStudio;
    @FXML private TextArea txtMediaDesc;
    @FXML private ComboBox<MediaType> cmbMediaType;

    @FXML private TextField txtPartNumber, txtPartTitle, txtPartYear, txtPartRating;
    @FXML private ComboBox<Status> cmbPartStatus;
    @FXML private DatePicker dpPartStarted, dpPartFinished;
    @FXML private TextArea txtPartNotes;

    /**
     * Initializes the controller. Sets up data entry masks and status-based rating locking.
     */
    @FXML
    public void initialize() {
        LOG.debug("Initializing MediaDialogController...");
        // Hydrate configuration option lists with core domain enums
        cmbMediaType.getItems().setAll(MediaType.values());
        cmbPartStatus.getItems().setAll(Status.values());
        cmbPartStatus.setValue(Status.PLANNING); // Safe database structural fallback default

        // Enforce basic alphanumeric data entry masks
        txtPartNumber.setTextFormatter(new TextFormatter<>(chg -> chg.getControlNewText().matches("\\d*") ? chg : null));
        txtPartYear.setTextFormatter(new TextFormatter<>(chg -> chg.getControlNewText().matches("\\d{0,4}") ? chg : null));
        txtPartRating.setTextFormatter(new TextFormatter<>(chg -> chg.getControlNewText().matches("([0-9]{0,2}(\\.[0-9]?)?)?") ? chg : null));

        // --- Database Constraint Rule Handler: Enforce Rating Trigger Integration ---
        cmbPartStatus.valueProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == Status.FINISHED || newStatus == Status.DROPPED) {
                txtPartRating.setDisable(false);
                txtPartRating.setPromptText("e.g. 8.5");
            } else {
                txtPartRating.setDisable(true);
                txtPartRating.clear(); // Wipe inputs instantly to prevent constraint errors
                txtPartRating.setPromptText("Locked (Requires Finished/Dropped)");
            }
        });

        // Trigger initialization routine evaluation
        txtPartRating.setDisable(true);
        txtPartRating.setPromptText("Locked (Requires Finished/Dropped)");
    }

    /**
     * Toggles layout structures based on whether we are adding a new media or a part to existing media.
     * @param selection The existing media selection, or null if creating new media.
     */
    public void setContext(Media selection) {
        if (selection != null) {
            LOG.debug("Setting context for existing media: {}", selection.getTitle());
            // Rule: Collapse root fields when linking elements onto an existing parent record
            mediaSection.setVisible(false);
            mediaSection.setManaged(false);

            // UX Automation: Pre-populate and guess next step sequence number
            int nextIncrementalStep = (selection.getParts() != null) ? selection.getParts().size() + 1 : 1;
            txtPartNumber.setText(String.valueOf(nextIncrementalStep));
        } else {
            LOG.debug("Setting context for new media entry.");
            mediaSection.setVisible(true);
            mediaSection.setManaged(true);
            txtPartNumber.setText("1");
        }
    }

    /**
     * Enforces database rule-validation parameters before processing writes.
     * @return true if input is valid, false otherwise.
     */
    public boolean isValidInput() {
        StringBuilder errorReport = new StringBuilder();

        // Enforce required root data if constructing new records
        if (mediaSection.isVisible()) {
            if (txtMediaTitle.getText() == null || txtMediaTitle.getText().trim().isEmpty()) {
                errorReport.append("• 'Media Title' cannot remain null.\n");
            }
            if (cmbMediaType.getValue() == null) {
                errorReport.append("• Valid 'Media Type' specification is mandatory.\n");
            }
        }

        // Validate structural part constraints
        if (txtPartNumber.getText().trim().isEmpty()) {
            errorReport.append("• 'Part Number' constraint definition required.\n");
        }
        if (cmbPartStatus.getValue() == null) {
            errorReport.append("• Selection 'Status' must be defined.\n");
        }

        // Numerical calculation score boundary enforcement
        if (!txtPartRating.getText().trim().isEmpty()) {
            try {
                double computedScore = Double.parseDouble(txtPartRating.getText().trim());
                if (computedScore < 0.0 || computedScore > 10.0) {
                    errorReport.append("• 'Rating' bounds must lie completely inside 0.0 to 10.0 scale.\n");
                }
            } catch (NumberFormatException e) {
                errorReport.append("• Malformed rating input format.\n");
            }
        }

        if (!errorReport.isEmpty()) {
            LOG.warn("Validation violation encountered in MediaDialog: {}", errorReport.toString().replace("\n", " "));
            Alert validationAlert = new Alert(Alert.AlertType.WARNING);
            validationAlert.setTitle("Validation Violation Encountered");
            validationAlert.setHeaderText("Missing or Invalid Field Criteria");
            validationAlert.setContentText(errorReport.toString());
            validationAlert.showAndWait();
            return false;
        }

        return true;
    }

    /**
     * Extracts Media entity data from the form.
     * @return A new Media entity populated with form data.
     */
    public Media getMediaInput() {
        Media target = new Media();
        target.setTitle(txtMediaTitle.getText().trim());
        target.setMediaType(cmbMediaType.getValue());
        target.setCreator(txtMediaCreator.getText().trim().isEmpty() ? null : txtMediaCreator.getText().trim());
        target.setStudio(txtMediaStudio.getText().trim().isEmpty() ? null : txtMediaStudio.getText().trim());
        target.setDescription(txtMediaDesc.getText().trim().isEmpty() ? null : txtMediaDesc.getText().trim());
        return target;
    }

    /**
     * Extracts MediaPart entity data from the form.
     * @return A new MediaPart entity populated with form data.
     */
    public MediaPart getMediaPartInput() {
        MediaPart part = new MediaPart();
        part.setPartNumber(Integer.parseInt(txtPartNumber.getText().trim()));
        part.setPartTitle(txtPartTitle.getText().trim().isEmpty() ? null : txtPartTitle.getText().trim());
        part.setStatus(cmbPartStatus.getValue());
        part.setNotes(txtPartNotes.getText().trim().isEmpty() ? null : txtPartNotes.getText().trim());

        if (!txtPartYear.getText().trim().isEmpty()) {
            part.setReleaseYear(Integer.parseInt(txtPartYear.getText().trim()));
        }
        
        // Database Trigger Fail-safe Optimization: Ignore completely if disabled or blank
        if (!txtPartRating.isDisabled() && !txtPartRating.getText().trim().isEmpty()) {
            part.setRating(Double.parseDouble(txtPartRating.getText().trim()));
        } else {
            part.setRating(null); // Explicit fallback definition ensures trigger safety compliance
        }

        part.setStartedAt(dpPartStarted.getValue());
        part.setFinishedAt(dpPartFinished.getValue());
        return part;
    }

    /**
     * Pre-populates the dialog fields with an existing log configuration for editing.
     * @param part The existing tracking record fetched from the database layer.
     */
    public void populateFields(MediaPart part) {
        if (part == null) return;
        LOG.debug("Populating dialog fields for part ID: {}", part.getId());

        Media media = part.getMedia();

        // 1. Populate top-level Media form section
        txtMediaTitle.setText(media.getTitle());
        cmbMediaType.setValue(media.getMediaType());
        txtMediaCreator.setText(media.getCreator() != null ? media.getCreator() : "");
        txtMediaStudio.setText(media.getStudio() != null ? media.getStudio() : "");
        txtMediaDesc.setText(media.getDescription() != null ? media.getDescription() : "");

        // 2. Populate tracking segment partition metrics
        txtPartNumber.setText(String.valueOf(part.getPartNumber()));
        txtPartTitle.setText(part.getPartTitle() != null ? part.getPartTitle() : "");
        txtPartYear.setText(part.getReleaseYear() != null ? String.valueOf(part.getReleaseYear()) : "");
        cmbPartStatus.setValue(part.getStatus());

        // Populate rating text safely
        if (part.getRating() != null) {
            txtPartRating.setText(String.valueOf(part.getRating()));
        } else {
            txtPartRating.setText("");
        }

        dpPartStarted.setValue(part.getStartedAt());
        dpPartFinished.setValue(part.getFinishedAt());
        txtPartNotes.setText(part.getNotes() != null ? part.getNotes() : "");
    }
}