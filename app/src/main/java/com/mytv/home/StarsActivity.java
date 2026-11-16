package com.mytv.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.List;

/** The girls' stars board: today's five prayers for each girl, and the week toward the crown. */
public class StarsActivity extends Activity {
    private static final int[] COLORS = {0xFFFF8FB1, 0xFF7FC8F8, 0xFFB79CED, 0xFF8BD3A6, 0xFFFFC857};
    private static final String[] DAYS = {"س", "ح", "ن", "ث", "ر", "خ", "ج"}; // Sat..Fri
    private static final int INK = 0xFF22314D;

    private LinearLayout board;
    private TextView hint;
    private int focusGirl = 0, focusPrayer = -1;

    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { build(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFAEE2FF, 0xFFFFD6EA, 0xFFFFF3C4}));
        root.addView(new JuzAmmaActivity.SkyView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(40), dp(22), dp(40), dp(18));
        col.addView(tv("⭐ نجوم البنات", 32, INK, true));
        TextView sub = tv("كل صلاة بنجمة  •  " + Stars.WEEK_GOAL + " نجمة في الأسبوع = تاج 👑  •  الأسبوع بيبدأ السبت", 15, 0xBB22314D, false);
        sub.setPadding(0, dp(2), 0, dp(14));
        col.addView(sub);
        board = new LinearLayout(this);
        board.setGravity(Gravity.CENTER);
        col.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        hint = tv("اختاري النجمة بتاعتك ودوسي OK بعد ما تصلّي 🤍", 16, INK, false);
        hint.setPadding(0, dp(12), 0, 0);
        col.addView(hint);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Stars.ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refresh, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refresh, f);
        build();
    }

    @Override
    protected void onPause() { super.onPause(); unregisterReceiver(refresh); }

    private void build() {
        board.removeAllViews();
        List<String> girls = Stars.girls(this);
        if (girls.isEmpty()) {
            board.addView(tv("ضيف أسامي البنات من صفحة «شاشتي» على الموبايل، في جزء «🏡 العيلة» 🤍", 22, INK, true));
            return;
        }
        LocalDate today = Stars.today(), ws = Stars.weekStart(today);
        View toFocus = null;
        for (int g = 0; g < girls.size(); g++) {
            String girl = girls.get(g);
            int color = COLORS[g % COLORS.length];
            boolean[] d = Stars.day(this, today, girl);
            int week = Stars.week(this, girl);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(18), dp(14), dp(18), dp(16));
            card.setBackground(round(0xF2FFFFFF, dp(26), color, dp(3)));
            card.setElevation(dp(8));

            TextView name = tv((week >= Stars.WEEK_GOAL ? "👑 " : "") + girl, 28, 0xFFFFFFFF, true);
            name.setPadding(dp(22), dp(4), dp(22), dp(4));
            name.setBackground(round(color, dp(20), 0, 0));
            name.setShadowLayer(4, 0, 1, 0x44000000);
            card.addView(name);

            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(14), 0, dp(10));
            for (int p = 0; p < 5; p++) {
                final int gi = g, pi = p;
                boolean on = d[p], due = Stars.due(p);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(4), dp(6), dp(4), dp(6));
                cell.setFocusable(true);
                cell.setClickable(true);
                TextView star = tv(on ? "⭐" : "☆", 34, on ? 0xFFFFB400 : (due ? 0xFF9AA5B8 : 0x449AA5B8), false);
                TextView pn = tv(Stars.PRAYERS[p], 12, due ? INK : 0x6622314D, true);
                cell.addView(star);
                cell.addView(pn);
                cell.setOnFocusChangeListener((v, has) -> {
                    v.setBackground(has ? round(0x33FFC857, dp(14), color, dp(3)) : null);
                    v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(120).start();
                    if (has) { focusGirl = gi; focusPrayer = pi; }
                });
                cell.setOnClickListener(v -> {
                    if (on) { hint.setText("النجمة دي خلاص بتاعتك 🌟 ولو عايزة تلغيها اطلبي من بابا أو ماما"); return; }
                    if (!due) { hint.setText("لسه ميعاد صلاة " + Stars.PRAYERS[pi] + " ما جاش ⏳"); return; }
                    Stars.set(this, girl, pi, true);
                    hint.setText("ما شاء الله يا " + girl + " ⭐ ربنا يتقبّل");
                });
                row.addView(cell, new LinearLayout.LayoutParams(dp(58), -2));
                if (g == focusGirl && p == focusPrayer) toFocus = cell;
                if (toFocus == null && g == 0 && p == 0) toFocus = cell;
            }
            card.addView(row);

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(Stars.WEEK_GOAL);
            pb.setProgress(Math.min(week, Stars.WEEK_GOAL));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            card.addView(pb, new LinearLayout.LayoutParams(dp(250), dp(10)));
            card.addView(tv("الأسبوع: " + week + " من " + Stars.WEEK_GOAL + (week >= Stars.WEEK_GOAL ? "  👑" : ""), 15, INK, true));

            LinearLayout days = new LinearLayout(this);
            days.setPadding(0, dp(8), 0, 0);
            for (int i = 0; i < 7; i++) {
                LocalDate day = ws.plusDays(i);
                int n = Stars.count(Stars.day(this, day, girl));
                LinearLayout dc = new LinearLayout(this);
                dc.setOrientation(LinearLayout.VERTICAL);
                dc.setGravity(Gravity.CENTER);
                TextView dn = tv(String.valueOf(n), 13, day.isAfter(today) ? 0x4422314D : INK, true);
                dn.setBackground(round(n == 5 ? color : (day.equals(today) ? 0x33FFC857 : 0x1122314D), dp(10), 0, 0));
                dc.addView(dn, new LinearLayout.LayoutParams(dp(26), dp(26)));
                dc.addView(tv(DAYS[i], 11, 0x9922314D, false));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
                dl.setMargins(dp(3), 0, dp(3), 0);
                days.addView(dc, dl);
            }
            card.addView(days);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
            cl.setMargins(dp(10), 0, dp(10), 0);
            board.addView(card, cl);
        }
        if (toFocus != null) toFocus.requestFocus();
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int r, int stroke, int sw) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(r);
        if (stroke != 0) d.setStroke(sw, stroke);
        return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
