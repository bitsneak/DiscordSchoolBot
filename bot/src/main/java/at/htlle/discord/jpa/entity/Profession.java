package at.htlle.discord.jpa.entity;

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
    private at.htlle.discord.model.enums.Profession name;
}
