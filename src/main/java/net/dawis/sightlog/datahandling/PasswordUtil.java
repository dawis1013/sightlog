package net.dawis.sightlog.datahandling;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Password encrypter and decrypter
 */
public class PasswordUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordUtil.class);

    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    public static boolean checkPassword(String rawPassword, String encodedPassword) {
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            LOG.error("Error occurred while checking password hash. ", e);
            return false;
        }
    }
}
