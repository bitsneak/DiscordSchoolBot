package at.htlle.discord.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Years {
    ONE("1"),
    TWO("2"),
    THREE("3"),
    FOUR("4"),
    FIVE("5");

    private final String year;

    // get the next year
    public Years getNextYear() {
        // next year's value is one higher than this year's
        int nextYearValue = Integer.parseInt(this.year) + 1;

        // find if a next year exists
        for (Years y : Years.values()) {
            if (Integer.parseInt(y.getYear()) == nextYearValue) {
                return y;
            }
        }
        // return null if there is no next year
        return null;
    }
}
