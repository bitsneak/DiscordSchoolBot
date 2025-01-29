package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Scholars;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name"}),
})
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Scholar
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    @NonNull
    private Scholars name;
}
