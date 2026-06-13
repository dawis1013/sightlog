package net.dawis.sightlog.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a media title (e.g., Book, Movie, Anime) tracked by a user.
 * Serves as the root entity for tracking individual parts or volumes.
 */
@Entity
@Table(name = "media")
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private MediaType mediaType;

    private String creator;
    private String studio;
    private String description;

    @Version
    private int version;

    @OneToMany(mappedBy = "media", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MediaPart> parts = new ArrayList<>();

    // getters, setters

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getStudio() {
        return studio;
    }

    public void setStudio(String studio) {
        this.studio = studio;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<MediaPart> getParts() {
        return parts;
    }

    public void setParts(List<MediaPart> parts) {
        this.parts = parts;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
