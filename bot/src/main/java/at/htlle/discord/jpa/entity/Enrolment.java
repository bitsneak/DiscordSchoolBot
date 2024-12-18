package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Enrolments;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Enrolment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    @NonNull
    private Enrolments name;

    @ManyToOne
    @JoinColumn(name = "year_id", nullable = false)
    @NonNull
    private Year year;
}
