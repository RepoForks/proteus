/*
 * Copyright 2016 Flipkart Internet Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.android.proteus.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;

import com.flipkart.android.proteus.EventType;
import com.flipkart.android.proteus.ImageLoaderCallback;
import com.flipkart.android.proteus.builder.DataAndViewParsingLayoutBuilder;
import com.flipkart.android.proteus.builder.LayoutBuilder;
import com.flipkart.android.proteus.builder.LayoutBuilderCallback;
import com.flipkart.android.proteus.builder.LayoutBuilderFactory;
import com.flipkart.android.proteus.demo.models.JsonResource;
import com.flipkart.android.proteus.parser.Parser;
import com.flipkart.android.proteus.toolbox.BitmapLoader;
import com.flipkart.android.proteus.toolbox.Styles;
import com.flipkart.android.proteus.view.ProteusView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class ProteusActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://10.1.8.7:8080/data/";
    private Retrofit retrofit;
    private JsonResource resources;

    private ViewGroup container;

    private DataAndViewParsingLayoutBuilder layoutBuilder;

    private JsonObject data;
    private JsonObject layout;

    private Styles styles;
    private Map<String, JsonObject> layouts;

    /**
     * Simple implementation of BitmapLoader for loading images from url in background.
     */
    private BitmapLoader bitmapLoader = new BitmapLoader() {
        @Override
        public Future<Bitmap> getBitmap(String imageUrl, View view) {
            return null;
        }

        @Override
        public void getBitmap(String imageUrl, final ImageLoaderCallback callback, View view, JsonObject layout) {

            URL url;

            try {
                url = new URL(imageUrl);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return;
            }

            new AsyncTask<URL, Integer, Bitmap>() {
                @Override
                protected Bitmap doInBackground(URL... params) {
                    try {
                        return BitmapFactory.decodeStream(params[0].openConnection().getInputStream());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                protected void onPostExecute(Bitmap result) {
                    callback.onResponse(result);
                }
            }.execute(url);
        }
    };

    /**
     * Implementation of LayoutBuilderCallback. This is where we get callbacks from proteus regarding
     * errors and events.
     */
    private LayoutBuilderCallback callback = new LayoutBuilderCallback() {
        @Override
        public void onUnknownAttribute(String attribute, JsonElement value, ProteusView view) {
            Log.i("unknown-attribute", attribute + " in " + view.getViewManager().getLayout().toString());
        }

        @Nullable
        @Override
        public ProteusView onUnknownViewType(String type, View parent, JsonObject layout, JsonObject data, int index, Styles styles) {
            return null;
        }

        @Override
        public JsonObject onLayoutRequired(String type, ProteusView parent) {
            return null;
        }

        @Override
        public void onViewBuiltFromViewProvider(ProteusView view, View parent, String type, int index) {

        }

        @Override
        public View onEvent(ProteusView view, JsonElement value, EventType eventType) {
            Log.d("event", value.toString());
            return (View) view;
        }

        @Override
        public PagerAdapter onPagerAdapterRequired(ProteusView parent, List<ProteusView> children, JsonObject layout) {
            return null;
        }

        @Override
        public Adapter onAdapterRequired(ProteusView parent, List<ProteusView> children, JsonObject layout) {
            return null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (null == retrofit) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        if (null == resources) {
            resources = retrofit.create(JsonResource.class);
        }

        // create a new DataAndViewParsingLayoutBuilder
        // and set layouts, callback and image loader.
        layoutBuilder = new LayoutBuilderFactory().getDataAndViewParsingLayoutBuilder(layouts);
        layoutBuilder.setListener(callback);
        layoutBuilder.setBitmapLoader(bitmapLoader);

        registerCustomViews(layoutBuilder);

        fetch();
    }

    private void registerCustomViews(LayoutBuilder layoutBuilder) {
        Parser parser = (Parser) layoutBuilder.getHandler("View");
        layoutBuilder.registerHandler("CircleView", new CircleViewParser(parser));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_proteus);

        // set the toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // handle refresh button click
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetch();
            }
        });

        container = (ViewGroup) findViewById(R.id.content_main);
    }

    private void render() {

        container.removeAllViews();

        layoutBuilder.setLayouts(layouts);

        // Inflate a new view using proteus
        ProteusView view = layoutBuilder.build(container, layout, data, 0, styles);

        container.addView((View) view);
    }

    private void fetch() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {

                    Call<JsonObject> call = resources.get("user.json");
                    data = call.execute().body();

                    call = resources.get("layout.json");
                    layout = call.execute().body();

                    Call<Map<String, JsonObject>> layoutsCall = resources.getLayouts();
                    layouts = layoutsCall.execute().body();

                    Call<Styles> stylesCall = resources.getStyles();
                    styles = stylesCall.execute().body();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                try {
                    render();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
