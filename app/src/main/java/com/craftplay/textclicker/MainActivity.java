package com.craftplay.textclicker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private Button btnToggle;
    private List<ClickItem> items;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Text Clicker");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(button("+ Añadir click", v -> edit(-1)));
        btnToggle = button("Iniciar", v -> toggle());
        root.addView(btnToggle);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button acc = button("Accesibilidad", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button log = button("Ver log", v -> showLog());
        row.addView(acc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(log, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);
        root.addView(list);

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        items = Store.load(this);
        refresh();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    // Dibuja la fila de clicks
    private void refresh() {
        list.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hay clicks. Toca \"+ Añadir click\".");
            empty.setTextColor(Color.GRAY);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            ClickItem it = items.get(i);
            TextView row = new TextView(this);
            row.setText((i + 1) + ".  " + it.timeString() + "   →   " + it.text);
            row.setTextSize(16);
            row.setTextColor(Color.BLACK);
            int pad = dp(14);
            row.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(240, 240, 240));
            bg.setCornerRadius(dp(10));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> edit(idx));
            list.addView(row);
        }
    }

    // Crear o editar un click (idx = -1 es nuevo)
    private void edit(int idx) {
        final boolean isNew = idx < 0;
        ClickItem cur = isNew ? new ClickItem("", 12, 0) : items.get(idx);
        final int[] sel = {cur.hour, cur.minute};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, p, p, 0);

        final EditText et = new EditText(this);
        et.setHint("Texto a tocar");
        et.setSingleLine(true);
        et.setText(cur.text);
        box.addView(et);

        final Button timeBtn = new Button(this);
        timeBtn.setText(String.format(Locale.getDefault(), "Hora: %02d:%02d", sel[0], sel[1]));
        timeBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            sel[0] = h;
            sel[1] = m;
            timeBtn.setText(String.format(Locale.getDefault(), "Hora: %02d:%02d", h, m));
        }, sel[0], sel[1], true).show());
        box.addView(timeBtn);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Nuevo click" : "Editar click " + (idx + 1))
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) {
                        Toast.makeText(this, "Falta el texto", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ClickItem n = new ClickItem(t, sel[0], sel[1]);
                    if (isNew) items.add(n);
                    else items.set(idx, n);
                    Store.save(this, items);
                    refresh();
                })
                .setNegativeButton("Cancelar", null);
        if (!isNew) {
            b.setNeutralButton("Borrar", (d, w) -> {
                items.remove(idx);
                Store.save(this, items);
                refresh();
            });
        }
        b.show();
    }

    // Iniciar o detener el proceso
    private void toggle() {
        if (RunnerService.running) {
            stopService(new Intent(this, RunnerService.class));
        } else {
            if (ClickerService.instance == null) {
                Toast.makeText(this, "Activa Text Clicker en Accesibilidad", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            if (items.isEmpty()) {
                Toast.makeText(this, "Añade al menos un click", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, RunnerService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        }
        ui.postDelayed(this::updateStatus, 300);
    }

    private void updateStatus() {
        boolean acc = ClickerService.instance != null;
        status.setText("Accesibilidad: " + (acc ? "activa" : "desactivada")
                + "\nProceso: " + (RunnerService.running ? "en marcha" : "detenido"));
        btnToggle.setText(RunnerService.running ? "Detener" : "Iniciar");
    }

    private void showLog() {
        TextView tv = new TextView(this);
        tv.setText(Store.readLog(this));
        tv.setTextIsSelectable(true);
        tv.setTextColor(Color.BLACK);
        int p = dp(16);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Log")
                .setView(sv)
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Limpiar", (d, w) -> Store.clearLog(this))
                .show();
    }

    private Button button(String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
