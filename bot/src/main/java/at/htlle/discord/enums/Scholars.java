package at.htlle.discord.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Scholars {
    TEACHER("Teacher"),
    STUDENT("Student");

    private final String name;
}
