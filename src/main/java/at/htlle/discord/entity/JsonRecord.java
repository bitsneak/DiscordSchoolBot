package at.htlle.discord.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JsonRecord
{
    @NonNull
    private String firstName;

    @NonNull
    private String lastName;

    @NonNull
    private String email;

    @NonNull
    private String className;

    @NonNull
    private Set<Profession> professions;

    @NonNull
    private Set<Role> roles;

    private Long discordId;

    private String discordName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime joinedAt;
}
