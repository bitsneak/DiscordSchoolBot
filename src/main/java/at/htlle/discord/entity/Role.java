package at.htlle.discord.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum Role
{
    ADMIN("Admin"),
    STUDENT("Schüler"),
    TEACHER("Lehrer");

    @Getter
    private final String name;
}
