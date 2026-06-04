package net.dawis.sightlog.datahandling;

import net.dawis.sightlog.entities.Media;
import net.dawis.sightlog.entities.MediaPart;
import net.dawis.sightlog.entities.MediaType;
import net.dawis.sightlog.entities.PartRewatch;
import net.dawis.sightlog.entities.Status;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HibernateConnectionTest {

    private SessionFactory sessionFactory;
    private java.sql.Connection keepAliveConnection;
    private String dbUrl;

    private SessionFactory buildTestFactory(String url) {
        Properties props = new Properties();
        props.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        props.setProperty("hibernate.connection.url", url);
        props.setProperty("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        props.setProperty("hibernate.hbm2ddl.auto", "none");
        props.setProperty("hibernate.show_sql", "true");
        props.setProperty("hibernate.type.preferred_uuid_jdbc_type", "VARCHAR");

        var registry = new StandardServiceRegistryBuilder().applySettings(props).build();
        var sources = new MetadataSources(registry);
        sources.addAnnotatedClass(Media.class);
        sources.addAnnotatedClass(MediaPart.class);
        sources.addAnnotatedClass(PartRewatch.class);

        return sources.buildMetadata().buildSessionFactory();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Generate a completely unique in-memory database identifier per test run to guarantee total isolation
        dbUrl = "jdbc:sqlite:file:testdb_" + UUID.randomUUID() + "?mode=memory&cache=shared&foreign_keys=on";

        // Open the keep-alive connection so the in-memory DB doesn't drop until the test completes
        keepAliveConnection = java.sql.DriverManager.getConnection(dbUrl);

        sessionFactory = buildTestFactory(dbUrl);

        // Initialize the structured database schema from file
        initSchema();
    }

    private void initSchema() throws IOException {
        String sql;
        try (InputStream is = HibernateConnectionTest.class
                .getClassLoader()
                .getResourceAsStream("db_model_lite.sql")) {
            assertNotNull(is, "db_model_lite.sql not found on classpath");
            sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Session session = openTestSession()) {
            session.doWork(conn -> {
                try (var stmt = conn.createStatement()) {
                    StringBuilder sb = new StringBuilder();
                    boolean inTrigger = false;

                    for (String line : sql.split("\\r?\\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                            continue;
                        }

                        sb.append(line).append("\n");

                        if (trimmed.toUpperCase().startsWith("CREATE TRIGGER")) {
                            inTrigger = true;
                        }

                        if (inTrigger) {
                            if (trimmed.toUpperCase().endsWith("END;")) {
                                inTrigger = false;
                                stmt.execute(sb.toString().trim());
                                sb.setLength(0);
                            }
                        } else if (trimmed.endsWith(";")) {
                            stmt.execute(sb.toString().trim());
                            sb.setLength(0);
                        }
                    }
                }
            });
        }
    }

    private Session openTestSession() {
        Session session = sessionFactory.openSession();
        session.doWork(conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
        );
        return session;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (keepAliveConnection != null) {
            keepAliveConnection.close();
        }
    }

    @Test
    void sessionOpensSuccessfully() {
        try (Session session = openTestSession()) {
            assertTrue(session.isOpen());
        }
    }

    @Test
    void foreignKeysAreEnabled() {
        try (Session session = openTestSession()) {
            session.doWork(conn -> {
                var rs = conn.createStatement().executeQuery("PRAGMA foreign_keys");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys should be ON (1)");
            });
        }
    }

    @Test
    void canPersistAndRetrieveMedia() {
        Media media = new Media();
        media.setTitle("Dune");
        media.setMediaType(MediaType.BOOK);
        media.setCreator("Frank Herbert");

        try (Session session = openTestSession()) {
            session.beginTransaction();
            session.persist(media);
            session.getTransaction().commit();
        }

        try (Session session = openTestSession()) {
            Media found = session.find(Media.class, media.getId());
            assertNotNull(found);
            assertEquals("Dune", found.getTitle());
            assertEquals(MediaType.BOOK, found.getMediaType());
        }
    }

    @Test
    void cascadeDeleteRemovesParts() {
        Media media = new Media();
        media.setTitle("Cascade Test");
        media.setMediaType(MediaType.MOVIE);

        MediaPart part = new MediaPart();
        part.setMedia(media);
        part.setPartNumber(1);
        part.setStatus(Status.PLANNING);
        media.getParts().add(part);

        try (Session session = openTestSession()) {
            session.beginTransaction();
            session.persist(media);
            session.getTransaction().commit();
        }

        Long partId = part.getId();

        try (Session session = openTestSession()) {
            session.beginTransaction();
            Media toDelete = session.find(Media.class, media.getId());
            session.remove(toDelete);
            session.getTransaction().commit();
        }

        try (Session session = openTestSession()) {
            MediaPart orphan = session.find(MediaPart.class, partId);
            assertNull(orphan, "Part should have been cascade deleted");
        }
    }

    @Test
    void ratingBlockedWhenStatusIsPlanning() {
        Media media = new Media();
        media.setTitle("Rating Block Test");
        media.setMediaType(MediaType.ANIME);

        MediaPart part = new MediaPart();
        part.setMedia(media);
        part.setPartNumber(1);
        part.setStatus(Status.PLANNING);
        part.setRating(8.0);
        media.getParts().add(part);

        try (Session session = openTestSession()) {
            session.beginTransaction();
            assertThrows(Exception.class, () -> {
                session.persist(media);
                session.getTransaction().commit();
            }, "DB trigger should block rating on planning status");
        }
    }

    @Test
    void ratingAcceptedWhenStatusIsFinished() {
        Media media = new Media();
        media.setTitle("Rating Accept Test");
        media.setMediaType(MediaType.MOVIE);

        MediaPart part = new MediaPart();
        part.setMedia(media);
        part.setPartNumber(1);
        part.setStatus(Status.FINISHED);
        part.setRating(9.0);
        part.setFinishedAt(LocalDate.now());
        media.getParts().add(part);

        try (Session session = openTestSession()) {
            session.beginTransaction();
            session.persist(media);
            assertDoesNotThrow(() -> session.getTransaction().commit());
        }

        try (Session session = openTestSession()) {
            MediaPart found = session.find(MediaPart.class, part.getId());
            assertEquals(9.0, found.getRating());
        }
    }

    @Test
    void rewatchArchivedOnRestart() {
        Media media = new Media();
        media.setTitle("Rewatch Test");
        media.setMediaType(MediaType.TV_SHOW);

        MediaPart part = new MediaPart();
        part.setMedia(media);
        part.setPartNumber(1);
        part.setStatus(Status.FINISHED);
        part.setRating(8.5);
        part.setFinishedAt(LocalDate.of(2024, 1, 10));
        media.getParts().add(part);

        try (Session session = openTestSession()) {
            session.beginTransaction();
            session.persist(media);
            session.getTransaction().commit();
        }

        try (Session session = openTestSession()) {
            session.beginTransaction();
            MediaPart managed = session.find(MediaPart.class, part.getId());
            managed.setStatus(Status.IN_PROGRESS);
            managed.setFinishedAt(null);
            session.getTransaction().commit();
        }

        try (Session session = openTestSession()) {
            session.doWork(conn -> {
                var rs = conn.createStatement().executeQuery(
                        "SELECT COUNT(*) FROM part_rewatch WHERE media_part_id = " + part.getId()
                );
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "Trigger should have archived the rating into part_rewatch");
            });
        }
    }

    @Test
    void prePopulatedSampleDataMapsCorrectly() {
        try (Session session = openTestSession()) {
            var query = session.createQuery("FROM Media WHERE title = :title", Media.class);
            query.setParameter("title", "Breaking Bad");
            Media breakingBad = query.getSingleResult();

            assertNotNull(breakingBad);
            assertEquals(net.dawis.sightlog.entities.MediaType.TV_SHOW, breakingBad.getMediaType());
            assertFalse(breakingBad.getParts().isEmpty(), "Should have loaded parts from sql script");

            assertNotNull(breakingBad.getParts().get(0).getStartedAt());
            assertEquals(LocalDate.of(2023, 1, 1), breakingBad.getParts().get(0).getStartedAt());
        }
    }

    @Test
    void hibernateSchemaValidationPasses() {
        Properties validationProps = new Properties();
        validationProps.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
        validationProps.setProperty("hibernate.connection.url", dbUrl); // Using the initialized test DB
        validationProps.setProperty("hibernate.dialect", "org.sqlite.JDBC");

        // Explicitly test the validate rule used in app.properties
        validationProps.setProperty("hibernate.hbm2ddl.auto", "validate");

        assertDoesNotThrow(() -> {
            var registry = new StandardServiceRegistryBuilder().applySettings(validationProps).build();
            var sources = new MetadataSources(registry);
            sources.addAnnotatedClass(Media.class);
            sources.addAnnotatedClass(MediaPart.class);
            sources.addAnnotatedClass(PartRewatch.class);

            try (SessionFactory factory = sources.buildMetadata().buildSessionFactory()) {
                assertNotNull(factory);
            }
        }, "Hibernate metadata configuration validation failed against real schema layout!");
    }

    @Test
    void optimisticLockingBlocksStaleEdits() throws Exception {
        // Create a physical temporary database file on your hard drive to test true file locking
        java.nio.file.Path tempDb = java.nio.file.Files.createTempFile("lock_test", ".db");
        String physicalUrl = "jdbc:sqlite:" + tempDb.toAbsolutePath() + "?busy_timeout=5000&foreign_keys=on";

        // Build two completely isolated factories to simulate Window A and Window B
        SessionFactory factoryA = buildTestFactory(physicalUrl);
        SessionFactory factoryB = buildTestFactory(physicalUrl);

        long mediaId;

        // Step 1: Create a baseline row using Instance A (version defaults to 1)
        try (Session s = factoryA.openSession()) {
            s.beginTransaction();
            Media m = new Media();
            m.setTitle("Original Title");
            m.setMediaType(MediaType.BOOK);
            s.persist(m);
            s.getTransaction().commit();
            mediaId = m.getId();
        }

        // Step 2: Instance A reads the row into memory
        Session sessionA = factoryA.openSession();
        Media mediaInInstanceA = sessionA.find(Media.class, mediaId);

        // Step 3: Instance B reads the exact same row into its independent memory context
        Session sessionB = factoryB.openSession();
        Media mediaInInstanceB = sessionB.find(Media.class, mediaId);

        // Step 4: Instance A updates the title and commits (bumps DB version to 2)
        sessionA.beginTransaction();
        mediaInInstanceA.setTitle("Changed by Window A");
        sessionA.getTransaction().commit();
        sessionA.close();

        // Step 5: Instance B tries to update using its stale copy (version 1)
        // This MUST throw an OptimisticLockException and block the save.
        sessionB.beginTransaction();
        mediaInInstanceB.setTitle("Changed by Window B");

        assertThrows(jakarta.persistence.OptimisticLockException.class, () -> {
            sessionB.getTransaction().commit();
        }, "Should have blocked Window B because its metadata was stale!");

        sessionB.close();
        factoryA.close();
        factoryB.close();

        // Clean up the temporary file from the operating system
        java.nio.file.Files.deleteIfExists(tempDb);
    }
}