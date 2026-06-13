package net.dawis.sightlog.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Represents a rewatch or reread event for a specific media part.
 */
@Entity
@Table(name = "part_rewatch")
public class PartRewatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_part_id", nullable = false)
    private MediaPart mediaPart;

    @Column(name = "rating", columnDefinition = "numeric(3,1)")
    private Double rating;

    @Column(name = "finished_at")
    private LocalDate finishedAt;

    private String notes;

    @Version
    private int version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MediaPart getMediaPart() {
        return mediaPart;
    }

    public void setMediaPart(MediaPart mediaPart) {
        this.mediaPart = mediaPart;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public LocalDate getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDate finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
