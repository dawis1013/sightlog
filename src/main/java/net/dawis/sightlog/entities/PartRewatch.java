package net.dawis.sightlog.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

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
}
