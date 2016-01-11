package jp.co.omron.plus_sensing.hvc_c2w_sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import jp.co.omron.hvcw.FileInfo;
import jp.co.omron.hvcw.HvcwApiWrapper;
import jp.co.omron.hvcw.Int;
import jp.co.omron.hvcw.OkaoResult;
import jp.co.omron.hvcw.ResultAeg;
import jp.co.omron.hvcw.ResultDetection;
import jp.co.omron.hvcw.ResultDirection;
import jp.co.omron.hvcw.ResultFace;
import jp.co.omron.hvcw.ResultGender;
import jp.co.omron.hvcw.ResultRecognition;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    /** Omronから取得したAPIキー */
    private static final String API_KEY = "API_KEY"; // 取得したキーに置き換える
    /** アプリケーションID */
    private static final int APP_ID = 100; // 開発者向けは現在100固定

    /** Web APIのリクエストURLのベース */
    private static final String SERVICE_URL = "https://developer.hvc.omron.com/c2w";
    /** Web APIアクセストークン */
    private String accessToken = "";
    /** Web APIアクセストークンの有効期限 */
    private int expiresIn;
    /** 登録されているカメラ情報のリスト */
    private List<CameraInfo> cameraList = new ArrayList<CameraInfo>();
    /** カメラに接続済みかどうかのフラグ */
    private boolean isConnected = false;

    private HvcwApiWrapper wrapper;

    // パスワードマスク切り替え用
    private EditText et1;
    private EditText et2;
    private CheckBox cb1;
    private CheckBox cb2;
    private int defaultInputType1;
    private int defaultInputType2;

    /** Wifi APスキャン用 */
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wrapper = new HvcwApiWrapper(API_KEY,APP_ID);

        // SDKのバージョン確認

        TextView tv = (TextView)findViewById(R.id.textView6);
        tv.setText("HVC SDK Ver." + wrapper.getVersion());

        // UIの設定
        initUIControl();

        // カメラ接続に必要なWifiのAPをスキャン
        scanWifiAP();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // レシーバーの解除
        unregisterReceiver(receiver);

        if (wrapper != null) {
            // カメラに接続済みの場合は切断する
            wrapper.disconnect();
        }
        super.onDestroy();
    }

    /**
     * UI Controlの初期化
     */
    private void initUIControl() {
        // EditTextのパスワード文字列マスク切り替え設定

        // ログインパスワード
        et1 = (EditText)findViewById(R.id.editText2);
        defaultInputType1 = et1.getInputType();
        cb1 = (CheckBox)findViewById(R.id.checkBox);
        cb1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cb1.isChecked()) {
                    et1.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | defaultInputType1);
                } else {
                    et1.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | defaultInputType1);
                }
                et1.setSelection(et1.getText().length());
            }
        });

        // Wifiパスワード
        et2 = (EditText)findViewById(R.id.editText3);
        defaultInputType2 = et2.getInputType();
        cb2 = (CheckBox)findViewById(R.id.checkBox2);
        cb2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cb2.isChecked()) {
                    et2.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | defaultInputType2);
                } else {
                    et2.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | defaultInputType2);
                }
                et2.setSelection(et2.getText().length());
            }
        });

        // Buttonのクリックイベント

        // Signup
        Button btn = (Button)findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText et = (EditText)findViewById(R.id.editText);
                SpannableStringBuilder sp = (SpannableStringBuilder)et.getText();
                String email = sp.toString();

                if (!email.isEmpty()) {
                    signup(email);
                }
            }
        });

        // Login
        btn = (Button)findViewById(R.id.button2);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText et = (EditText)findViewById(R.id.editText);
                SpannableStringBuilder sp = (SpannableStringBuilder)et.getText();
                String email = sp.toString();

                et = (EditText)findViewById(R.id.editText2);
                sp = (SpannableStringBuilder)et.getText();
                String password = sp.toString();

                if (!email.isEmpty() && !password.isEmpty()) {
                    login(email, password);
                }
            }
        });

        // Logout
        btn = (Button)findViewById(R.id.button3);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        // Connect
        btn = (Button)findViewById(R.id.button4);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Spinner spinner = (Spinner)findViewById(R.id.spinner);
                String ssid = (String)spinner.getSelectedItem();

                EditText et = (EditText)findViewById(R.id.editText3);
                SpannableStringBuilder sp = (SpannableStringBuilder)et.getText();
                String password = sp.toString();

                if (!ssid.isEmpty() && !password.isEmpty()) {
                    connect(ssid, password);
                }
            }
        });

        // Reload
        btn = (Button)findViewById(R.id.button5);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getCameraList();
            }
        });

        // Set
        btn = (Button)findViewById(R.id.button6);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCamera();
            }
        });

        // Rename
        btn = (Button)findViewById(R.id.button7);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Spinner sp = (Spinner)findViewById(R.id.spinner2);
                int index = sp.getSelectedItemPosition();
                if (index >= cameraList.size()) {
                    addLog("error");
                    return;
                }
                final String cameraID = cameraList.get(index).getID();
                String cameraName = cameraList.get(index).getName();
                String macAddress = cameraList.get(index).getMacAddress();

                String newName;
                if (cameraName.equals("HVC-C2W")) {
                    newName = String.format("%s_%s", cameraName, macAddress.substring(8).toUpperCase());
                } else {
                    newName = "HVC-C2W";
                }
                rename(cameraID, newName);
            }
        });

        // Execute
        btn = (Button)findViewById(R.id.button8);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                execute();
            }
        });

        // Register
        btn = (Button)findViewById(R.id.button9);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerAlbum();
            }
        });

        // Delete
        btn = (Button)findViewById(R.id.button10);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAlbum();
            }
        });
    }

    /**
     * TextViewに表示されているログの末尾に文字列を追加
     * @param text 追加する文字列
     */
    private void addLog(String text) {
        // ログの最後に追加
        TextView tv = (TextView)findViewById(R.id.textView6);
        tv.append("\n" + text);

        // 追加部分が見えるようにスクロール
        final ScrollView sv = (ScrollView) findViewById(R.id.scrollView);
        sv.post(new Runnable() {
            @Override
            public void run() {
                sv.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    /**
     * Wifiのアクセスポイントをスキャン
     */
    private void scanWifiAP() {
        final WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    List<ScanResult> results = wifiManager.getScanResults();
                    ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_spinner_item);
                    for (int i = 0; i < results.size(); ++i) {
                        String ssid = results.get(i).SSID;
                        // SSIDが空白のものは登録しない
                        if (!ssid.isEmpty()) {
                            adapter.add(ssid);
                        }
                    }

                    // Spinnerに設定
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    Spinner sp = (Spinner)findViewById(R.id.spinner);
                    sp.setAdapter(adapter);
                }
            };
            registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        }
        wifiManager.startScan();
    }

    /**
     * ユーザー登録
     * @param mailAddress メールアドレス
     */
    private void signup(String mailAddress) {
        addLog("signup ->");

        String url = SERVICE_URL + "/api/v1/signup.php";
        String apiKey = "apiKey=" + API_KEY;
        String email = "email=" + mailAddress;
        String params = apiKey + "&" + email;
        Log.d(TAG, "url:" + url);
        Log.d(TAG, "params:" + params);

        PostMessageTask task = new PostMessageTask(new Listener() {
            public void onReceived(String json) {
                if (json != null) {
                    Log.d(TAG, "json:" + json);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
                        JSONObject result = jsonObject.getJSONObject("result");
                        String code = result.getString("code");
                        String msg = result.getString("msg");
                        addLog(String.format("response=%s(%s)", code, msg));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "json:null");
                    addLog("error");
                }
            }
        });

        task.execute(url, params);
    }

    /**
     * ログイン
     * @param mailAddress メールアドレス
     * @param password パスワード
     */
    private void login(String mailAddress, String password) {
        addLog("login ->");

        String url = SERVICE_URL + "/api/v1/login.php";
        String apiKey = "apiKey=" + API_KEY;
        // deviceIdは端末のMACアドレスを使用
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String deviceId = "deviceId=" + wifiInfo.getMacAddress();
        String osType = "osType=" + "1";
        String email = "email=" + mailAddress;
        String pass = "password=" + password;
        String params = apiKey + "&" + deviceId + "&" + osType + "&" + "&" + email + "&" + pass;
        Log.d(TAG, "url:" + url);
        Log.d(TAG, "params:" + params);

        PostMessageTask task = new PostMessageTask(new Listener() {
            public void onReceived(String json) {
                if (json != null) {
                    Log.d(TAG, "json:" + json);

                    try {
                        JSONObject root = new JSONObject(json);
                        JSONObject result = root.getJSONObject("result");
                        String code = result.getString("code");
                        String msg = result.getString("msg");
                        if (msg.equals("success")) {
                            JSONObject access = root.getJSONObject("access");
                            accessToken = access.getString("token");
                            expiresIn = access.getInt("expiresIn");
                        }
                        addLog(String.format("response=%s(%s)", code, msg));
                        addLog(String.format("token=\"%s\"", accessToken));
                        addLog(String.format("expiresIn=%d", expiresIn));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "json:null");
                    addLog("error");
                }
            }
        });

        task.execute(url, params);
    }

    /**
     * ログアウト
     */
    private void logout() {
        addLog("logout ->");

        String url = SERVICE_URL + "/api/v1/logout.php";
        Log.d(TAG, "url:" + url);

        PostMessageTask task = new PostMessageTask(new Listener() {
            public void onReceived(String json) {
                if (json != null) {
                    Log.d(TAG, "json:" + json);

                    try {
                        JSONObject root = new JSONObject(json);
                        JSONObject result = root.getJSONObject("result");
                        String code = result.getString("code");
                        String msg = result.getString("msg");
                        if (msg.equals("success")) {
                            accessToken = "";
                            expiresIn = 0;
                        }
                        addLog(String.format("response=%s(%s)", code, msg));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "json:null");
                    addLog("error");
                }
                accessToken = null;
            }
        });

        task.execute(url, "", accessToken);
    }

    /**
     * カメラをネットワークに接続してサービスに登録
     */
    private void connect(String ssid, String password) {
        addLog("connect ->");

        final String fileName = getApplicationContext().getFilesDir() + "/network_setting.pcm";

        // カメラを登録するためのネットワーク設定音声ファイルを作成
        wrapper.generateDataSoundFile(fileName, ssid, password,
                accessToken, new HvcwApiWrapper.GenerateDataSoundFileListener() {
                    @Override
                    public void onResult(final String fileName, final String ssid, String password, String accessToken) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "play:" + fileName);
                                File networkSettingFile = new File(fileName);
                                if (networkSettingFile == null) {
                                    return;
                                }

                                byte[] byteData = new byte[(int) networkSettingFile.length()];
                                FileInputStream fis;
                                try {
                                    fis = new FileInputStream(networkSettingFile);
                                    fis.read(byteData);
                                    fis.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                    return;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return;
                                }

                                int audioBuffSize = AudioTrack.getMinBufferSize(
                                        8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

                                AudioTrack audio = new AudioTrack(AudioManager.STREAM_MUSIC,
                                        8000,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        audioBuffSize,
                                        AudioTrack.MODE_STREAM);
                                audio.play();
                                audio.write(byteData, 0, byteData.length);
                            }
                        }).start();
                    }

                    @Override
                    public void onError(String fileName, String ssid, String password, String accessToken,
                                        HvcwApiWrapper.ErrorStatus error) {
                        addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
                    }
                });
    }

    /**
     * 登録されたカメラのリストを取得
     */
    private void getCameraList() {
        addLog("getCameraList ->");

        String url = SERVICE_URL + "/api/v1/getCameraList.php";
        Log.d(TAG, "url:" + url);

        PostMessageTask task = new PostMessageTask(new Listener() {
            public void onReceived(String json) {
                if (json != null) {
                    Log.d(TAG, "json:" + json);

                    try {
                        JSONObject root = new JSONObject(json);
                        JSONObject result = root.getJSONObject("result");
                        String code = result.getString("code");
                        String msg = result.getString("msg");
                        addLog(String.format("response=%s(%s)", code, msg));
                        if (msg.equals("success")) {
                            cameraList.clear();
                            ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_spinner_item);
                            JSONArray array = root.getJSONArray("cameraList");
                            for (int i = 0; i < array.length(); ++i) {
                                JSONObject obj =array.getJSONObject(i);
                                CameraInfo ci = new CameraInfo();
                                String id = obj.getString("cameraId");
                                ci.setID(id);
                                String name = obj.getString("cameraName");
                                ci.setName(name);
                                ci.setMacAddress(obj.getString("cameraMacAddr"));
                                ci.setAppID(obj.getString("appId"));
                                ci.setOwnerType(obj.getInt("ownerType"));
                                ci.setOwnerEmail(obj.getString("ownerEmail"));
                                cameraList.add(ci);
                                adapter.add(name);
                                addLog(String.format("camera[%d] name=\"%s\",id=\"%s\"", i, name, id));
                            }

                            // Spinnerに設定
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            Spinner sp = (Spinner)findViewById(R.id.spinner2);
                            sp.setAdapter(adapter);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "json:null");
                    addLog("error");
                }
            }
        });

        task.execute(url, "", accessToken);
    }

    /**
     * 使用するカメラを設定
     */
    private void setCamera() {
        addLog("setCamera ->");

        // 選択されているカメラのIDを取得
        Spinner sp = (Spinner)findViewById(R.id.spinner2);
        int index = sp.getSelectedItemPosition();
        if (index >= cameraList.size()) {
            addLog("error");
            return;
        }
        final String cameraID = cameraList.get(index).getID();

        wrapper.setCamera(cameraID, accessToken, new HvcwApiWrapper.CameraConnectListener() {
            @Override
            public void onConnect(String cameraID) {
            }

            @Override
            public void onError(String cameraID, HvcwApiWrapper.ErrorStatus error) {
                addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
            }
        });


    }

    /**
     * カメラ名のリネーム
     * @param id カメラID
     * @param name 新しいカメラ名
     */
    private void rename(String id, String name) {
        addLog("rename ->");

        String url = SERVICE_URL + "/api/v1/renameCamera.php";
        String cameraId = "cameraId=" + id;
        String newName = "newName=" + name;
        String params = cameraId + "&" + newName;
        Log.d(TAG, "url:" + url);
        Log.d(TAG, "params:" + params);

        PostMessageTask task = new PostMessageTask(new Listener() {
            public void onReceived(String json) {
                if (json != null) {
                    Log.d(TAG, "json:" + json);

                    try {
                        JSONObject root = new JSONObject(json);
                        JSONObject result = root.getJSONObject("result");
                        String code = result.getString("code");
                        String msg = result.getString("msg");
                        addLog(String.format("response=%s(%s)", code, msg));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "json:null");
                    addLog("error");
                }
            }
        });

        task.execute(url, params, accessToken);
    }

    /**
     * 検出・推定を実行
     */
    private void execute() {
        addLog("execute ->");

        // 顔検出・顔向き推定・年齢推定・性別推定・顔認証をON
        HvcwApiWrapper.OkaoUseFunction useFunction = wrapper.new OkaoUseFunction(
                false, false, false, true, true, true, true, false, false, false, true);
        OkaoResult result = new OkaoResult();
        Int returnStatus = new Int();
        // 実行

        wrapper.okaoExecute(useFunction, new HvcwApiWrapper.OkaoExecuteListener() {
            @Override
            public void onResult(OkaoResult result) {
                StringBuilder sb = new StringBuilder();
                // 検出数
                int count = result.getResultFaces().getCount();
                ResultFace[] rf = result.getResultFaces().getResultFace();
                sb.append(String.format("faceCount=%d", count));
                for (int i = 0; i < count; ++i) {
                    // 顔検出結果
                    sb.append(String.format("\nface[%d] x=%d,y=%d,size=%d,confidence=%d", i,
                            rf[i].getCenter().getX(),
                            rf[i].getCenter().getY(),
                            rf[i].getSize(),
                            rf[i].getConfidence()));
                    // 顔向き推定結果
                    ResultDirection rd = rf[i].getDirection();
                    sb.append(String.format("\nface[%d] leftRight=%d,upDown=%d,roll=%d", i,
                            rd.getLR(),
                            rd.getUD(),
                            rd.getRoll()));
                    // 年齢推定結果
                    ResultAeg ra = rf[i].getAge();
                    sb.append(String.format("\nface[%d] age=%d,confidence=%d", i,
                            ra.getAge(),
                            ra.getConfidence()));
                    // 性別推定結果
                    ResultGender rg = rf[i].getGender();
                    sb.append(String.format("\nface[%d] gender=%d,confidence=%d", i,
                            rg.getGender(),
                            rg.getConfidence()));
                    // 顔認証
                    ResultRecognition rr = rf[i].getRecognition();
                    String recg;
                    switch (rr.getUID()) {
                        case -128:
                            recg = "not recognized";
                            break;
                        case -127:
                            recg = "album is not registered";
                            break;
                        case -1:
                            recg = String.format("match=×,score=%d", rr.getScore());
                            break;
                        default:
                            recg = String.format("match=○,score=%d", rr.getScore());
                    }
                    sb.append(String.format("\nface[%d] %s", i, recg));
                }
                addLog(sb.toString());
            }

            @Override
            public void onError(HvcwApiWrapper.ErrorStatus error) {
                addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
            }
        });

    }

    /**
     * 顔認証アルバム登録
     */
    private void registerAlbum() {
        addLog("registerAlbum ->");

        // アルバム登録
        wrapper.registerAlbum(0, 0, new HvcwApiWrapper.RegisterAlbumListener() { // UserID=0,DataID=0へ登録
            @Override
            public void onResult(int userID, int dataID, ResultDetection resultDetection, FileInfo fileInfo) {
                StringBuilder sb = new StringBuilder();

                // 顔検出結果
                sb.append(String.format("register album x=%d,y=%d,size=%d,confidence=%d",
                        resultDetection.getCenter().getX(),
                        resultDetection.getCenter().getY(),
                        resultDetection.getSize(),
                        resultDetection.getConfidence()));
                addLog(sb.toString());
            }

            @Override
            public void onError(int userID, int dataID, HvcwApiWrapper.ErrorStatus error) {
                addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
            }
        });
    }

    /**
     * 顔認証アルバム削除
     */
    private void deleteAlbum() {
        addLog("deleteAlbum ->");
        // アルバムデータ削除
        wrapper.deleteAlbum(0, 0, new HvcwApiWrapper.DeleteAlbumListener() { // UserID=0,DataID=0のデータを削除
            @Override
            public void onResult(int userID, int dataID) {
                addLog(String.format("delete album UserId=%d,DateID=%d", userID, dataID));
            }

            @Override
            public void onError(int userID, int dataID, HvcwApiWrapper.ErrorStatus error) {
                addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
            }
        });
    }

    private void getLastOkaoImage() {
        addLog("getLastOkaoImage ->");
        wrapper.getLastOkaoImage(new HvcwApiWrapper.LastOkaoImageListener() {
            @Override
            public void onResult(Bitmap bitmap) {
                addLog(String.format("size:%d", bitmap.getByteCount()));
            }

            @Override
            public void onError(HvcwApiWrapper.ErrorStatus error) {
                addLog(String.format("errorCode=%d,description=\"%s\"", error.getErrorCode(), error.getDescription()));
            }
        });
    }

    /**
     * POST処理のリスナー
     */
    public interface Listener {
        void onReceived(String json);
    }

    /**
     * リクエストURLにPOSTするタスク
     */
    public class PostMessageTask extends AsyncTask<String, Void, String> {

        /** POST処理のリスナー */
        private Listener listener = null;

        /**
         * コンストラクタ
         * @param listener POST処理のリスナー
         */
        public PostMessageTask(Listener listener) {
            this.listener = listener;
        }

        /**
         * バックグラウンド処理.
         * POSTしてその結果を返す
         * @param params [リクエストURL, アクセストークン（不要時は空）, パラメータ]
         * @return POST結果（HTTTPのエラーの場合はnull）
         */
        @Override
        protected String doInBackground(String... params) {
            HttpsURLConnection conn = null;
            String json = null;
            try {
                Log.d("PostSecureMessageTask", "connecting...");
                URL url;
                if (params[1].isEmpty()) {
                    // パラメータなし
                    url = new URL(params[0]);
                } else {
                    // パラメータがある場合はURLに連結
                    url = new URL(params[0] + "?" + params[1]);
                }
                conn = (HttpsURLConnection)url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                if (params.length == 3) {
                    // アクセストークンが必要なリクエストの場合
                    conn.setRequestProperty("Authorization", "Bearer " + params[2]);
                }
                conn.setDoInput(true);
                conn.setDoOutput(true);

                // POST
                conn.connect();

                // レスポンス受信
                if (conn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                    Log.d("PostMessageTask", "response 200");
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    json = sb.toString();
                } else {
                    Log.d("PostMessageTask", "response " + conn.getResponseCode());
                }
            } catch(MalformedURLException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            } finally {
                if(conn != null) {
                    Log.d("PostMessageTask", "disconnecting...");
                    conn.disconnect();
                }
            }

            return json;
        }

        @Override
        protected void onPostExecute(String param) {
            listener.onReceived(param);
        }
    }
}
