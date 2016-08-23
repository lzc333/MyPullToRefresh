package com.liuzhcheng.mypulltorefresh;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends Activity {
    private MyRefreshView myRefreshView;
    private ListView listView;
    private String[] datas={"dfdf","dfdfff","dfdfdfdf","yiyuii","jklpklf","dfdfff","dfdfdfdf","yiyuii","jklpklf"
            ,"dfdfff","dfdfdfdf","yiyuii","jklpklf","dfdfff","dfdfdfdf","yiyuii","jklpklf"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView=(ListView) findViewById(R.id.listView);
        myRefreshView=(MyRefreshView) findViewById(R.id.myRefreshView);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,datas);
        listView.setAdapter(adapter);
    }
}
