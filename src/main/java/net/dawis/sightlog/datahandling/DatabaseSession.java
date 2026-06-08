package net.dawis.sightlog.datahandling;

import net.dawis.sightlog.entities.Media;
import net.dawis.sightlog.entities.MediaPart;
import net.dawis.sightlog.entities.PartRewatch;
import net.dawis.sightlog.entities.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Manages the Hibernate SessionFactory and provides access to database sessions.
 */
public class DatabaseSession {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSession.class);

    private static final SessionFactory SESSION_FACTORY = buildSessionFactory();

    /**
     * Initializes the Hibernate SessionFactory using configuration from app.properties.
     * @return A configured SessionFactory instance.
     * @throws ExceptionInInitializerError if configuration fails or app.properties is missing.
     */
    private static SessionFactory buildSessionFactory() {
        Properties props = new Properties();

        try (InputStream input = DatabaseSession.class
                .getClassLoader()
                .getResourceAsStream("app.properties")) {

            if (input == null) {
                LOG.error("Configuration file 'app.properties' not found on classpath.");
                throw new IOException("app.properties not found");
            }

            props.load(input);

            ServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(props)
                    .build();

            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(User.class);
            sources.addAnnotatedClass(Media.class);
            sources.addAnnotatedClass(MediaPart.class);
            sources.addAnnotatedClass(PartRewatch.class);

            return sources.buildMetadata().buildSessionFactory();

        } catch (IOException e) {
            LOG.error("Failed to initialize database session factory: {}", e.getMessage());
            throw new ExceptionInInitializerError("Could not initialize DatabaseSession: " + e.getMessage());
        }
    }

    /**
     * Opens a new Hibernate session.
     * @return A new Session object.
     */
    public static Session open() {
        LOG.debug("DB session opened.");
        return SESSION_FACTORY.openSession();
    }

    /**
     * Shuts down the SessionFactory and releases all resources.
     */
    public static void shutdown() {
        LOG.info("Shutting down DB session factory.");
        SESSION_FACTORY.close();
    }
}