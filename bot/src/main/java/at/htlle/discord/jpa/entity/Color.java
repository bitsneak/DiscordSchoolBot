package at.htlle.discord.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "color", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"scope", "color"}),
})
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "scope", nullable = false, unique = true)
    @NonNull
    private String scope;

    // hex code for the color with the #
    @Column(name = "color", nullable = false)
    @NonNull
    private String color;
}
