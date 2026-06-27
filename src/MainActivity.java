package net.davidgf.elremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final IrCodec.State st = new IrCodec.State();
    private ConsumerIrManager ir;
    private SharedPreferences prefs;
    private TextView display;
    private Button powerBtn, swingBtn, timerBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = getPreferences(Context.MODE_PRIVATE);
        load();

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        display = new TextView(this);
        display.setTextSize(22);
        display.setGravity(Gravity.CENTER);
        display.setPadding(0, pad, 0, pad);
        root.addView(display);

        powerBtn = btn("POWER", v -> {
            st.power = !st.power;
            clearTimer();                      // power overrides any armed timer
            send(IrCodec.ACT_POWER);
        });
        root.addView(powerBtn);

        root.addView(row(
            btn("TEMP -", v -> { if (st.temp > 16) st.temp--; send(IrCodec.ACT_TEMP); }),
            btn("TEMP +", v -> { if (st.temp < 32) st.temp++; send(IrCodec.ACT_TEMP); })));

        root.addView(row(
            btn("MODE",  v -> { st.mode  = (st.mode  + 1) % IrCodec.MODES.length;  send(IrCodec.ACT_MODE); }),
            btn("SPEED", v -> { st.speed = (st.speed + 1) % IrCodec.SPEEDS.length; send(IrCodec.ACT_FAN); })));

        swingBtn = btn("SWING", v -> { st.swing = !st.swing; send(IrCodec.ACT_SWING); });
        root.addView(swingBtn);

        timerBtn = btn("TIMER", v -> {
            if (st.timerEnabled) { clearTimer(); send(IrCodec.ACT_TIMER); }  // armed -> cancel
            else promptTimer();
        });
        root.addView(timerBtn);

        root.addView(btn("BLAST (resend)", v -> send(IrCodec.ACT_SETTLED)));

        setContentView(root);
        refresh();
    }

    /** Send the full state over IR on every press, then update the UI. */
    private void send(int action) {
        byte[] frame = IrCodec.encode(st, action);

        StringBuilder hex = new StringBuilder();
        for (byte b : frame) hex.append(String.format("%02X ", b));
        android.util.Log.d("ACIR", hex.toString().trim());

        if (ir != null && ir.hasIrEmitter()) {
            try {
                ir.transmit(IrCodec.CARRIER_HZ, IrCodec.toPattern(frame));
            } catch (Exception e) {
                android.util.Log.e("ACIR", "transmit failed", e);
                Toast.makeText(this, "transmit failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No IR emitter on this device", Toast.LENGTH_SHORT).show();
        }
        refresh();
        save();
    }

    /** Persist preferred settings (not the timer — it's a one-shot command). */
    private void save() {
        prefs.edit()
            .putBoolean("power", st.power)
            .putInt("temp", st.temp)
            .putInt("mode", st.mode)
            .putInt("speed", st.speed)
            .putBoolean("swing", st.swing)
            .apply();
    }

    private void load() {
        st.power = prefs.getBoolean("power", false);
        st.temp  = prefs.getInt("temp", 24);
        st.mode  = prefs.getInt("mode", 1);
        st.speed = prefs.getInt("speed", 0);
        st.swing = prefs.getBoolean("swing", false);
        clearTimer();   // always start disarmed
    }

    private void clearTimer() {
        st.timerEnabled = false; st.timerHours = 0; st.timerMins = 0;
    }

    /** Prompt for a delay; direction depends on power state (ON->off-timer, OFF->on-timer). */
    private void promptTimer() {
        NumberPicker hp = new NumberPicker(this);
        hp.setMinValue(0); hp.setMaxValue(24);
        hp.setValue(st.timerHours > 0 ? st.timerHours : 1);

        NumberPicker mp = new NumberPicker(this);
        mp.setMinValue(0); mp.setMaxValue(59);
        mp.setFormatter(i -> String.format("%02d", i));
        mp.setValue(st.timerMins);

        int pad = dp(16);
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(pad, pad, pad, pad);
        box.addView(hp);
        TextView colon = new TextView(this);
        colon.setText("h  :  m"); colon.setPadding(pad, 0, pad, 0);
        box.addView(colon);
        box.addView(mp);

        new AlertDialog.Builder(this)
            .setTitle("Turn " + (st.power ? "OFF" : "ON") + " after")
            .setView(box)
            .setPositiveButton("Set", (d, w) -> {
                int h = hp.getValue(), m = mp.getValue();
                if (h == 24) m = 0;                 // cap at 24h
                if (h == 0 && m == 0) { clearTimer(); send(IrCodec.ACT_TIMER); return; }
                st.timerHours = h; st.timerMins = m; st.timerEnabled = true;
                send(IrCodec.ACT_TIMER);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refresh() {
        String line = (st.power ? "ON" : "OFF") + "   " + st.temp + "\u00B0C\n" +
            IrCodec.MODES[st.mode] + "   FAN " + IrCodec.SPEEDS[st.speed] +
            "   SWING " + (st.swing ? "ON" : "OFF");
        if (st.timerEnabled)
            line += String.format("\nTIMER \u2192 %s in %dh%02d",
                st.power ? "OFF" : "ON", st.timerHours, st.timerMins);
        display.setText(line);
        glow(powerBtn, st.power, "#4CAF50");
        glow(swingBtn, st.swing, "#4CAF50");
        glow(timerBtn, st.timerEnabled, "#F44336");
    }

    private void glow(Button b, boolean on, String color) {
        b.setBackgroundColor(on ? Color.parseColor(color) : Color.parseColor("#CCCCCC"));
        b.setTextColor(on ? Color.WHITE : Color.BLACK);
    }

    private Button btn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout row(View a, View c) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        r.setLayoutParams(rp);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        la.rightMargin = dp(4);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lc.leftMargin = dp(4);
        a.setLayoutParams(la);
        c.setLayoutParams(lc);
        r.addView(a); r.addView(c);
        return r;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
