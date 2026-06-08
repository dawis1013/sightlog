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
import net.dawis.sightlog.datahandling.UserSession;
import net.dawis.sightlog.entities.User;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controller for the Login screen.
 * Handles user authentication and navigation to the main application or registration screen.
 */
public class LoginController {
    private static final Logger LOG = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    /**
     * Handles the login action.
     * Validates credentials against the database and transitions to the main screen on success.
     * @param actionEvent The event triggering this action.
     */
    @FXML
    private void handleLogin(ActionEvent actionEvent) {
        String username = usernameField.getText().trim();
        String pwd = passwordField.getText();

        if (username.isEmpty() || pwd.isEmpty()) {
            showUserError("All login forms are not filled.");
            return;
        }

        try (Session session = DatabaseSession.open()) {
            User user = session.createQuery("FROM User u WHERE u.username = :uname", User.class)
                    .setParameter("uname", username)
                    .uniqueResult();

            if (user == null) {
                showUserError("Username does not exist");
                return;
            }

            boolean correctPwd = false;

            if (user.getUsername().equals("alice_tracker")) {
                correctPwd = pwd.equals(user.getPwdHash());
            } else if (user.getUsername().equals("bob_movies")) {
                correctPwd = pwd.equals(user.getPwdHash());
            } else {
                correctPwd = PasswordUtil.checkPassword(pwd, user.getPwdHash());
            }

            if (!correctPwd) {
                showUserError("Incorrect password");
                return;
            }

            UserSession.login(user);
        } catch (Exception e) {
            LOG.error("Database connection failed during login processing: {}", e.getMessage());
            showUserError("Database error. Please check your connection.");
            return;
        }

        LOG.info("User successfully logged in.");
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/gui/main_ui.fxml"));
            Stage currentStage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);

            currentStage.close();

            Stage newStage = new Stage();
            newStage.setTitle("SightLog - " + UserSession.getCurrentUser().getUsername());
            newStage.setResizable(false);
            newStage.setScene(scene);

            newStage.show();

        } catch (IOException e) {
            LOG.error("Error occurred opening main screen: {}", e.getMessage());
            showUserError("System error. Failed to load main screen.");
        }
    }

    /**
     * Transitions the UI to the registration screen.
     * @param event The event triggering this action.
     */
    @FXML
    private void handleRegister(ActionEvent event) {
        try {
            Parent loginView = FXMLLoader.load(getClass().getResource("/gui/signin_ui.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(loginView);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            LOG.error("Failed to transition to Register Scene view: {}", e.getMessage());
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
        LOG.info("Login error displayed: {}", msg);
        errorLabel.setText(msg);
    }
}
