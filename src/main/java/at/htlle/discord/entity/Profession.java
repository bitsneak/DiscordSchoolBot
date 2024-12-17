package at.htlle.discord.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum Profession
{
    IT("IT"),
    L("L"),
    M("M"),
    R("R");

    @Getter
    private final String name;
}
