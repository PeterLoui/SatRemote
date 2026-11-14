package com.example.satremote;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    // Remote buttons shown on screen. Codes are NEC commands (0-255) saved per button.
    static final String[][] ROWS = {
        {"POWER", "MUTE", "MENU", "EXIT"},
        {"INFO", "EPG", "FAV", "TV/RADIO"},
        {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"RECALL", "0", "LIST"},
        {"VOL-", "UP", "VOL+"}, {"LEFT", "OK", "RIGHT"}, {"CH-", "DOWN", "CH+"},
        {"RED", "GREEN", "YELLOW", "BLUE"}
    };

    ConsumerIrManager ir;
    SharedPreferences prefs;
    EditText addrField, cmdField;
    Spinner assignSpinner;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("codes", MODE_PRIVATE);
        ir = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 48);
        sv.addView(root);
        setContentView(sv);

        TextView status = new TextView(this);
        boolean ok = ir != null && ir.hasIrEmitter();
        status.setText(ok ? "IR transmitter found" : "No IR transmitter on this phone");
        status.setTextColor(ok ? Color.rgb(0, 130, 0) : Color.RED);
        root.addView(status);

        for (String[] row : ROWS) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (String name : row) {
                Button btn = new Button(this);
                btn.setText(name);
                btn.setOnClickListener(v -> sendButton(name));
                r.addView(btn, new LinearLayout.LayoutParams(0, 150, 1f));
            }
            root.addView(r);
        }

        // ---- Code finder: find which NEC command does what, then assign it to a button ----
        TextView h = new TextView(this);
        h.setText("\nCode finder (NEC, 38 kHz)");
        h.setTextSize(18);
        root.addView(h);

        addrField = new EditText(this);
        addrField.setHint("Address in hex, e.g. 00 or 4040");
        addrField.setText(prefs.getString("addr", "00"));
        root.addView(addrField);

        cmdField = new EditText(this);
        cmdField.setHint("Command 0-255 (decimal)");
        cmdField.setInputType(InputType.TYPE_CLASS_NUMBER);
        cmdField.setText("0");
        root.addView(cmdField);

        LinearLayout nav = new LinearLayout(this);
        Button prev = new Button(this); prev.setText("< Prev");
        Button send = new Button(this); send.setText("Send");
        Button next = new Button(this); next.setText("Next >");
        nav.addView(prev, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(send, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(nav);
        prev.setOnClickListener(v -> { step(-1); sendFinder(); });
        next.setOnClickListener(v -> { step(1); sendFinder(); });
        send.setOnClickListener(v -> sendFinder());

        assignSpinner = new Spinner(this);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (String[] row : ROWS) for (String n : row) names.add(n);
        assignSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        root.addView(assignSpinner);

        Button save = new Button(this);
        save.setText("Save this command to the selected button");
        save.setOnClickListener(v -> {
            String name = (String) assignSpinner.getSelectedItem();
            prefs.edit().putString("addr", addrField.getText().toString().trim())
                .putInt("cmd_" + name, cmdValue()).apply();
            toast(name + " = " + cmdValue());
        });
        root.addView(save);

        TextView tip = new TextView(this);
        tip.setText("How to use: point the phone's IR window at the receiver. Press Next until the receiver reacts, "
            + "pick the button it matches, tap Save. If nothing reacts for any command, change the address "
            + "(common values: 00, 40, 04, 4040, 00FF) and scan again.");
        tip.setPadding(0, 16, 0, 0);
        root.addView(tip);
    }

    int cmdValue() {
        try { return Integer.parseInt(cmdField.getText().toString().trim()) & 0xFF; }
        catch (Exception e) { return 0; }
    }

    void step(int d) { cmdField.setText(String.valueOf((cmdValue() + d + 256) & 0xFF)); }

    int addrValue(String s) {
        try { return Integer.parseInt(s.trim(), 16) & 0xFFFF; } catch (Exception e) { return 0; }
    }

    void sendFinder() { sendNec(addrValue(addrField.getText().toString()), cmdValue()); }

    void sendButton(String name) {
        if (!prefs.contains("cmd_" + name)) { toast("No code saved for " + name + " yet. Use the code finder."); return; }
        sendNec(addrValue(prefs.getString("addr", "00")), prefs.getInt("cmd_" + name, 0));
    }

    // NEC: 9 ms mark + 4.5 ms space, 32 bits LSB first (mark 560us, space 560/1690us), stop mark.
    void sendNec(int addr, int cmd) {
        if (ir == null || !ir.hasIrEmitter()) { toast("This phone has no IR transmitter"); return; }
        long frame;
        if (addr <= 0xFF) frame = (addr & 0xFF) | ((~addr & 0xFF) << 8);   // classic NEC
        else frame = addr & 0xFFFF;                                        // extended NEC
        frame |= ((long) (cmd & 0xFF) << 16) | ((long) (~cmd & 0xFF) << 24);

        int[] p = new int[2 + 64 + 1];
        int i = 0;
        p[i++] = 9000; p[i++] = 4500;
        for (int bit = 0; bit < 32; bit++) {
            p[i++] = 560;
            p[i++] = ((frame >> bit) & 1) == 1 ? 1690 : 560;
        }
        p[i++] = 560;
        ir.transmit(38000, p);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
