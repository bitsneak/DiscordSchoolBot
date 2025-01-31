package at.htlle.discord.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "year_id"}),
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

    public Enrolment(@NonNull String name, Teacher teacher, @NonNull Year year) {
        this.name = name;
        this.teacher = teacher;
        this.year = year;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @NonNull
    private String name;

    @ManyToOne
    @JoinColumn(name = "teacher_id", unique = true)
    private Teacher teacher;

    @ManyToOne
    @JoinColumn(name = "year_id", nullable = false)
    @NonNull
    private Year year;
}
