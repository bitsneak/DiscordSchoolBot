package at.htlle.discord.jpa.entity;

import at.htlle.discord.model.enums.Years;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "year", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"year"}),
})
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Year {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "year", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    @NonNull
    private Years year;
}
