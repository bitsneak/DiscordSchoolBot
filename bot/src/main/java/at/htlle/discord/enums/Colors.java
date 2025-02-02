package at.htlle.discord.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.*;

@Getter
@AllArgsConstructor
public enum Colors {
    GENERIC(java.awt.Color.decode("#94a5a7"));

    private final Color color;
}
