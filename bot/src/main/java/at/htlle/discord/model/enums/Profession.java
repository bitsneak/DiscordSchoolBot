package at.htlle.discord.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Profession {
    IT("IT"),
    L("L"),
    M("M"),
    R("R");

    private final String name;
}
