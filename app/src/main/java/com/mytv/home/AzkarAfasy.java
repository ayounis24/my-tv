package com.mytv.home;

/**
 * Morning / evening azkar read by Sheikh Mishary Alafasy: one continuous recording each
 * (hosted on Internet Archive, listed by makkahlive.net), with the second each zikr starts
 * and each repetition begins, so the screen and the counter follow his voice.
 * While these lists are empty the app uses the per-zikr Hisn al-Muslim audio instead.
 */
public final class AzkarAfasy {
    public static final String MORNING_URL = "https://archive.org/download/makkah-live.-net-athkar-01/MakkahLive.Net_athkar_03.mp3";
    public static final String EVENING_URL = "https://archive.org/download/makkah-live.-net-athkar-01/MakkahLive.Net_athkar_04.mp3";

    public static final class Cue {
        public final String text, evening;
        public final int repeat;
        public final float start, end;
        public final float[] reps;
        Cue(String text, String evening, int repeat, float start, float end, float[] reps) {
            this.text = text; this.evening = evening; this.repeat = repeat; this.start = start; this.end = end; this.reps = reps;
        }
    }

    public static final Cue[] MORNING = {};
    public static final Cue[] EVENING = {};
}
