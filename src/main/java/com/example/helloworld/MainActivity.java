package com.example.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("🚀 Hello World from GitHub Codespaces!\n\nСкомпилировано мгновенно в облаке!");
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);
    }
}
