package net.dawis.sightlog.datahandling;

import net.dawis.sightlog.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserSession {
    private static final Logger LOG = LoggerFactory.getLogger(UserSession.class);

    private static User currentUser = null;

    public static void login(User user) {
        if (currentUser == null) {
            UserSession.currentUser = user;
            LOG.info("{} logged in.", user.getUsername());
        } else {
            LOG.warn("User is already logged in.");
        }
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void logout() {
        if (currentUser != null) {
            LOG.info("{} logged out.", currentUser.getUsername());
            UserSession.currentUser = null;
        } else {
            LOG.warn("User is already logged out.");
        }
    }
}
