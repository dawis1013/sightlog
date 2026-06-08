package net.dawis.sightlog.datahandling;

import net.dawis.sightlog.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the singleton state of the currently authenticated user.
 */
public class UserSession {
    private static final Logger LOG = LoggerFactory.getLogger(UserSession.class);

    private static User currentUser = null;

    /**
     * Sets the current user if no user is already logged in.
     * @param user The user to log in.
     */
    public static void login(User user) {
        if (currentUser == null) {
            UserSession.currentUser = user;
            LOG.info("{} logged in.", user.getUsername());
        } else {
            LOG.warn("User is already logged in.");
        }
    }

    /**
     * Returns the currently authenticated user.
     * @return The current User, or null if no user is logged in.
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Clears the current user session.
     */
    public static void logout() {
        if (currentUser != null) {
            LOG.info("{} logged out.", currentUser.getUsername());
            UserSession.currentUser = null;
        } else {
            LOG.warn("User is already logged out.");
        }
    }
}
