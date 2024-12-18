package at.htlle.discord.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Enrolments {
    FOUR_AIT("4AIT"),
    FOUR_BIT("4BIT"),
    FOUR_RL("4RL"),
    FOUR_M("4M"),

    FIVE_IT("5IT"),
    FIVE_LIT("5LIT"),
    FIVE_M("5M"),
    FIVE_R("5R");

    private final String name;
}
