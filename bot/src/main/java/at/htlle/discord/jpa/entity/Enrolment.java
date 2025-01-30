package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Years;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "teacher_id", "year_id"})
})
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Enrolment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @NonNull
    private String name;

    @ManyToOne
    @JoinColumn(name = "teacher_id", nullable = false)
    @NonNull
    private Teacher classTeacher;

    @ManyToOne
    @JoinColumn(name = "year_id", nullable = false)
    @NonNull
    private Year year;
}
