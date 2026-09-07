package com.forexpilot.ai;

import java.time.*;

public final class MarketClock {

    public static boolean isForexOpen(Instant instant) {
        ZonedDateTime n = instant.atZone(ZoneId.of("America/New_York"));
        DayOfWeek d = n.getDayOfWeek();
        LocalTime t = n.toLocalTime();

        if (d == DayOfWeek.SATURDAY) return false;
        if (d == DayOfWeek.SUNDAY) return !t.isBefore(LocalTime.of(17, 5));
        if (d == DayOfWeek.FRIDAY) return t.isBefore(LocalTime.of(16, 59));

        return !(!t.isBefore(LocalTime.of(16, 59))
                && t.isBefore(LocalTime.of(17, 5)));
    }

    public static String session(Instant i) {
        ZonedDateTime s = i.atZone(ZoneId.of("Australia/Sydney"));
        ZonedDateTime t = i.atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime l = i.atZone(ZoneId.of("Europe/London"));
        ZonedDateTime n = i.atZone(ZoneId.of("America/New_York"));

        StringBuilder x = new StringBuilder();

        if (between(s.toLocalTime(), LocalTime.of(8, 0), LocalTime.of(17, 0))) {
            x.append("Sydney ");
        }

        if (between(t.toLocalTime(), LocalTime.of(9, 0), LocalTime.of(18, 0))) {
            x.append("Tokyo ");
        }

        if (between(l.toLocalTime(), LocalTime.of(8, 0), LocalTime.of(17, 0))) {
            x.append("London ");
        }

        if (between(n.toLocalTime(), LocalTime.of(8, 0), LocalTime.of(17, 0))) {
            x.append("New York ");
        }

        return x.length() == 0 ? "Off-session" : x.toString().trim();
    }

    private static boolean between(LocalTime t, LocalTime a, LocalTime b) {
        return !t.isBefore(a) && t.isBefore(b);
    }
}
