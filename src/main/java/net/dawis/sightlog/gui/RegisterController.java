package net.dawis.sightlog.gui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.dawis.sightlog.datahandling.DatabaseSession;
import net.dawis.sightlog.datahandling.PasswordUtil;
import net.dawis.sightlog.entities.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

/**
 * Controller for the Registration screen.
 * Handles new user account creation.
 */
public class RegisterController {
    private static final Logger LOG = LoggerFactory.getLogger(RegisterController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    /**
     * Handles the registration submission.
     * Validates input and persists the new user to the database.
     * @param actionEvent The event triggering this action.
     */
    @FXML
    public void handleRegisterSubmit(ActionEvent actionEvent) {
        String username = usernameField.getText().trim();
        String pwd = passwordField.getText();

        if (username.isEmpty() || pwd.isEmpty()) {
            showUserError("All registration forms must be filled");
            return;
        }

        if (username.contains(" ")) {
            showUserError("Username must not have spaces");
            return;
        }

        if (username.length() < 4 || pwd.length() < 4) {
            showUserError("Credentials must have at least 4 characters");
            return;
        }

        Transaction tx = null;
        try (Session session = DatabaseSession.open()) {
            User existing = session.createQuery("FROM User u WHERE u.username = :uname", User.class)
                    .setParameter("uname", username)
                    .uniqueResult();

            if (existing != null) {
                showUserError("Username is already taken.");
                return;
            }

            tx = session.beginTransaction();
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPwdHash(PasswordUtil.hashPassword(pwd));
            newUser.setCreatedAt(OffsetDateTime.now());

            session.persist(newUser);
            tx.commit();
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            LOG.error("Database error during registration: {}", e.getMessage());
            showUserError("Registration failed. Database error.");
            return;
        }

        LOG.info("User {} successfully registered.", username);
        handleBackToLogin(actionEvent);
    }

    /**
     * Transitions the UI back to the login screen.
     * @param event The event triggering this action.
     */
    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            Parent loginView = FXMLLoader.load(getClass().getResource("/gui/login_ui.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(loginView);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            LOG.error("Failed to transition back to Login Scene view: {}", e.getMessage());
        }
    }

    /**
     * Minimizes the application window.
     * @param event The event triggering this action.
     */
    @FXML
    public void handleMinimizeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    /**
     * Closes the application.
     * @param event The event triggering this action.
     */
    @FXML
    public void handleCloseWindow(ActionEvent event) {
        LOG.info("Application terminating...");
        Platform.exit();
    }

    /**
     * Displays an error message to the user and logs it.
     * @param msg The message to display.
     */
    private void showUserError(String msg) {
        LOG.info("Registration error displayed: {}", msg);
        errorLabel.setText(msg);
    }
}
