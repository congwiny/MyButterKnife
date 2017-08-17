package com.congwiny.mybutterknife;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.congwiny.inject.InjectView;
import com.example.BindView;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.text_view)
    TextView myTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InjectView.bind(this);
        Toast.makeText(this,"textview=="+myTextView,Toast.LENGTH_LONG).show();
    }
}
