package at.htlle.discord.jpa.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "client", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"discord_id", "enrolment_id"}),
        @UniqueConstraint(columnNames = {"discord_id", "enrolment_id", "joined_at"}),
        @UniqueConstraint(columnNames = {"discord_id", "profession_id"}),
        @UniqueConstraint(columnNames = {"discord_id", "enrolment_id", "joined_at", "left_at"}),
        @UniqueConstraint(columnNames = {"discord_name", "email", "scholar_id"})
})
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "discord_id", nullable = false, unique = true)
    @NonNull
    private String discordId;

    @Column(name = "discord_name", nullable = false)
    @NonNull
    private String discordName;

    @Column(name = "email", nullable = false, unique = true)
    @NonNull
    private String email;

    @ManyToOne
    @JoinColumn(name = "enrolment_id")
    private Enrolment enrolment;

    @ManyToOne
    @JoinColumn(name = "profession_id")
    private Profession profession;

    @ManyToOne
    @JoinColumn(name = "scholar_id", nullable = false)
    @NonNull
    private Scholar scholar;

    @Column(name = "joined_at", nullable = false)
    @NonNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime leftAt;
}
