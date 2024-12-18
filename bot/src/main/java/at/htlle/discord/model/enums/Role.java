package at.htlle.discord.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Role {
    TEACHER("Lehrer"),
    STUDENT("Schüler");

    private final String name;
}
