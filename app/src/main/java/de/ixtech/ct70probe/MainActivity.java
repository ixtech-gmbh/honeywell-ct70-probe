package de.ixtech.ct70probe;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows the recorded test results. */
public class MainActivity extends Activity {

    protected boolean showContinue() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        if (showContinue()) {
            root.addView(continueButton());
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(this);
        text.setText(TokenTest.results(this));
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        content.addView(text);
        if (showContinue()) {
            content.addView(continueButton());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll);
        setContentView(root);
    }

    private Button continueButton() {
        Button next = new Button(this);
        next.setText("Continue setup");
        next.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
        return next;
    }
}
