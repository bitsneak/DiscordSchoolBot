package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Professions;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "profession")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Profession
{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    @NonNull
    private Professions name;
}
