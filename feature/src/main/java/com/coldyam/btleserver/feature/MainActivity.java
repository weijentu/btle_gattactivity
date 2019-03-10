package com.coldyam.btleserver.feature;

import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class MainActivity extends GattActivity {

    /* Tag */
    private static final String TAG = MainActivity.class.getSimpleName();

    private Button button0;
    private Button button1;
    private Button button2;
    private Button button3;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        button0 = this.findViewById(R.id.button0);
        button1 = this.findViewById(R.id.button1);
        button2 = this.findViewById(R.id.button2);
        button3 = this.findViewById(R.id.button3);
        textView = this.findViewById(R.id.textView0);
    }

    @Override
    public void receiveString(final String string) {
        runOnUiThread(new Runnable() {
            public void run() {
                textView.setText("接收讯息：" + string);
            }
        });
    }

    public void onClick0(View v) {
        try {
            List <String> list = new ArrayList<String>();
            list.add("我身体不太舒服");
            list.add("我今天不太舒服");
            list.add("我不太舒服");
            list.add("我感觉不舒服");
            list.add("我今天不舒服");
            list.add("我不舒服");
            JSONArray options = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                options.put(list.get(i));
            }
            JSONObject object = new JSONObject();
            object.put("question", "我在，有什么可以帮你？");
            object.put("id", 1);
            object.put("options", options);
            Log.d(TAG, object.toString());
            notifyJSONObject(object);
        } catch (Throwable t) {
            Log.e(TAG, "Could not parse malformed JSON");
        }
    }

    public void onClick1(View v) {
        try {
            List <String> list = new ArrayList<String>();
            list.add("女性");
            list.add("男性");
            list.add("我是女性");
            list.add("我是男性");
            JSONArray options = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                options.put(list.get(i));
            }
            JSONObject object = new JSONObject();
            object.put("question", "您好！请问您是男性还是女性？");
            object.put("id", 2);
            object.put("options", options);
            Log.d(TAG, object.toString());
            notifyJSONObject(object);
        } catch (Throwable t) {
            Log.e(TAG, "Could not parse malformed JSON");
        }
    }

    public void onClick2(View v) {
        try {
            List <String> list = new ArrayList<String>();
            list.add("咳嗽");
            list.add("我咳嗽");
            JSONArray options = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                options.put(list.get(i));
            }
            JSONObject object = new JSONObject();
            object.put("question", "请问您有什么症状？");
            object.put("id", 3);
            object.put("options", options);
            Log.d(TAG, object.toString());
            notifyJSONObject(object);
        } catch (Throwable t) {
            Log.e(TAG, "Could not parse malformed JSON");
        }
    }

    public void onClick3(View v) {
        try {
            List <String> list = new ArrayList<String>();
            list.add("有痰");
            list.add("有咳痰");
            list.add("咳痰");
            JSONArray options = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                options.put(list.get(i));
            }
            JSONObject object = new JSONObject();
            object.put("question", "请问您咳痰还是不咳痰？");
            object.put("id", 3);
            object.put("options", options);
            Log.d(TAG, object.toString());
            notifyJSONObject(object);
        } catch (Throwable t) {
            Log.e(TAG, "Could not parse malformed JSON");
        }
    }
}
