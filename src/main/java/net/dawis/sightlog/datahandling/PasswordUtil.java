package net.dawis.sightlog.datahandling;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for hashing and verifying passwords using BCrypt.
 */
public class PasswordUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordUtil.class);

    /**
     * Hashes a raw password using BCrypt with a default salt rounds of 12.
     * @param rawPassword The plain text password to hash.
     * @return The hashed password string.
     */
    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    /**
     * Verifies a raw password against an encoded hash.
     * @param rawPassword The plain text password.
     * @param encodedPassword The hashed password to check against.
     * @return true if the password matches the hash, false otherwise.
     */
    public static boolean checkPassword(String rawPassword, String encodedPassword) {
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            LOG.error("Error occurred while checking password hash: {}", e.getMessage());
            return false;
        }
    }
}
