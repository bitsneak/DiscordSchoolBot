package at.htlle.discord.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "year")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Year {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "year", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    @NonNull
    private at.htlle.discord.model.enums.Year year;
}
