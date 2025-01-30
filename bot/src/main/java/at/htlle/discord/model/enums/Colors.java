package at.htlle.discord.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.*;

@Getter
@AllArgsConstructor
public enum Colors {

    GENERIC(java.awt.Color.decode("#94a5a7")),
    CLASS(java.awt.Color.decode("#e64c3d")),
    IT(java.awt.Color.decode("#f0c50f")),
    L(java.awt.Color.decode("#9a59b7")),
    M(java.awt.Color.decode("#3599db")),
    R(java.awt.Color.decode("#2ecc71"));

    private final Color color;
}
