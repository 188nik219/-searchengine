package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")})
@Entity(name = "page")
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String indexedPath;
}
