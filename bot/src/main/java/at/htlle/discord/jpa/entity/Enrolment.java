package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Years;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    @NonNull
    private String name;

    @ManyToOne
    @JoinColumn(name = "class_teacher_id", nullable = false, unique = true)
    @NonNull
    private Teacher classTeacher;

    @ManyToOne
    @JoinColumn(name = "year_id", nullable = false)
    @NonNull
    private Year year;

    public void updateNameWithYear(Years newYear) {
        String currentName = getName();
        // extract the current class suffix
        String suffix = currentName.substring(1);
        // update the name with the new year
        setName(newYear.getYear() + suffix);
    }
}
