package at.htlle.discord.jpa.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Client
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    // TODO make it optional so teachers can also join
    @ManyToOne
    @JoinColumn(name = "enrolment_id", nullable = false)
    @NonNull
    private Enrolment enrolment;

    @ManyToOne
    @JoinColumn(name = "profession_id", nullable = false)
    @NonNull
    private Profession profession;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    @NonNull
    private Scholar scholar;

    @Column(name = "joined_at", nullable = false)
    @NonNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime joinedAt;
}
