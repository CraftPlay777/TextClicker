package com.craftplay.textclicker;

import java.util.Calendar;
import java.util.Locale;

public class ClickItem {
    public final String text;
    public final int hour;
    public final int minute;

    public ClickItem(String text, int hour, int minute) {
        this.text = text;
        this.hour = hour;
        this.minute = minute;
    }

    public String timeString() {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    // Milisegundos que faltan para la hora de hoy (negativo si ya pasó)
    public long millisUntil() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() - System.currentTimeMillis();
    }
}
