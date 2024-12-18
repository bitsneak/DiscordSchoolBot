package at.htlle.discord.jpa.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

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
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "discord_id", nullable = false, unique = true)
    @NonNull
    private Long discordId;

    @Column(name = "discord_name", nullable = false)
    @NonNull
    private String discordName;

    @Column(name = "email", nullable = false, unique = true)
    @NonNull
    private String email;

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
    private Role role;

    @Column(name = "joined_at", nullable = false)
    @NonNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime leftAt;
}
