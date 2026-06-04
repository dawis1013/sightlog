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

public class DatabaseSession {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSession.class);

    private static final SessionFactory SESSION_FACTORY = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        Properties props = new Properties();

        try (InputStream input = DatabaseSession.class
                .getClassLoader()
                .getResourceAsStream("app.properties")) {

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
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Session open() {
        LOG.debug("DB session opened.");
        return SESSION_FACTORY.openSession();
    }

    public static void shutdown() {
        LOG.debug("DB session closed");
        SESSION_FACTORY.close();
    }
}