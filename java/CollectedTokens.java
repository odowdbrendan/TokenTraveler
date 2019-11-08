package com.example.tokentravmarker;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.ArrayList;

public class CollectedTokens extends AppCompatActivity {


    MainActivity mainActivity = new MainActivity();
    public ArrayList<String> collectedTokens = mainActivity.collectedTokens;
    int arraySize = collectedTokens.size();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collected_tokens);
    }
}
