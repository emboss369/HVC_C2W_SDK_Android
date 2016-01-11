package jp.co.omron.hvcw;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

/**
 * Created by hiroaki on 2016/01/10.
 */
public class HvcwApiWrapper {

    private static HvcwApiWrapper instance = new HvcwApiWrapper();

    /**
     * HVC SDK ハンドル
     */
    private static HvcwApi api;

    /**
     * Omronから取得したAPIキー
     */
    private String apiKey;

    /**
     * アプリケーションID
     */
    private int appID;

    /**
     * カメラに接続済みかどうかのフラグ
     */
    private boolean isConnected = false;

    private Handler handler = new Handler();

    static {
        System.loadLibrary("openh264");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("ldpc");
        System.loadLibrary("IOTCAPIs");
        System.loadLibrary("RDTAPIs");
        System.loadLibrary("c2w");
        System.loadLibrary("HvcOi");
        System.loadLibrary("HVCW");
    }

    private HvcwApiWrapper(){}

    public HvcwApiWrapper(String apiKey, int appID) {
        this.apiKey = apiKey;
        this.appID = appID;
        if (api != null) {
            api.deleteHandle();
        }
        // ハンドル生成
        api = HvcwApi.createHandle();
    }

    public void deleteWrapper() {
        api.deleteHandle();
        api = null;
    }

    public String getVersion() {
        Int major = new Int();
        Int minor = new Int();
        Int release = new Int();
        HvcwApi.getVersion(major, minor, release);
        return String.format("%d.%d.%d", major.getIntValue(), minor.getIntValue(), release.getIntValue());
    }

    public interface CameraConnectListener {
        void onConnect(String cameraID);
        void onError(String cameraID, ErrorStatus error);
    }

    public class ErrorStatus {
        private int errorCode;
        private int returnStatus;
        private String description;

        ErrorStatus(int errorCode, int returnStatus){
            setErrorCode(errorCode);
            this.returnStatus = returnStatus;
        }

        public String getDescription() {
            return description;
        }
        public int getReturnStatus() {
            return returnStatus;
        }

        public int getErrorCode() {
            return errorCode;
        }

        private void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
            switch (errorCode) {
                case ErrorCodes.HVCW_SUCCESS:
                    description = "Successed processing";
                    break;
                case ErrorCodes.HVCW_INVALID_PARAM:
                    description = "Invalid argument";
                    break;
                case ErrorCodes.HVCW_NOT_READY:
                    description = "HVC is not ready";
                    break;
                case ErrorCodes.HVCW_BUSY:
                    description = "HVC can not accept the process(BUSY)";
                    break;
                case ErrorCodes.HVCW_NOT_SUPPORT:
                    description = "Requested process is not supported";
                    break;
                case ErrorCodes.HVCW_TIMEOUT:
                    description = "Communication timeout occurs";
                    break;
                case ErrorCodes.HVCW_NOT_FOUND:
                    description = "Missing processing";
                    break;
                case ErrorCodes.HVCW_FAILURE:
                    description = "Every other error code";
                    break;
                case ErrorCodes.HVCW_NOT_INITIALIZE:
                    description = "ISDK is uninitialized";
                    break;
                case ErrorCodes.HVCW_DISCONNECTED:
                    description = "Disconnected of the camera";
                    break;
                case ErrorCodes.HVCW_NOHANDLE:
                    description = "Handle error";
                    break;
                case ErrorCodes.HVCW_NO_FACE:
                    description = "No face";
                    break;
                case ErrorCodes.HVCW_PLURAL_FACES:
                    description = "Plural face";
                    break;
                case ErrorCodes.HVCW_INVALID_RECEIVEDATA:
                    description = "Invalid received data";
                    break;
            }
        }
    }

    public void setCamera(final String cameraID, final String accessToken, final CameraConnectListener listener) {
        // APIは通信を行うので非同期で呼び出す
        new Thread(new Runnable() {
            @Override
            public void run() {
                // カメラに接続
                int ret = api.connect(cameraID, accessToken);
                Int returnStatus = new Int();
                if (ret == ErrorCodes.HVCW_SUCCESS) {
                    isConnected = true;
                    ret = api.setAppID(appID, returnStatus);
                    if (ret == ErrorCodes.HVCW_SUCCESS) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onConnect(cameraID);
                            }
                        });

                    } else {
                        final int retval = ret;
                        final int retsts = returnStatus.getIntValue();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onError(cameraID, new ErrorStatus(retval, retsts));
                            }
                        });
                    }
                } else {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(cameraID, new ErrorStatus(retval, retsts));
                        }
                    });
                }
            }
        }).start();
    }

    public interface GenerateDataSoundFileListener {
        void onResult(String fileName, String ssid, String password, String accessToken);
        void onError(String fileName, String ssid, String password, String accessToken, ErrorStatus error);
    }
    public void generateDataSoundFile(String fileName, String ssid, String password,
                                      String accessToken, GenerateDataSoundFileListener listener) {
        int ret = api.generateDataSoundFile(fileName, ssid, password, accessToken);
        if (ret == ErrorCodes.HVCW_SUCCESS) {
            listener.onResult(fileName,ssid,password,accessToken);
        } else {
            listener.onError(fileName, ssid, password, accessToken ,new ErrorStatus(ret,0));
        }
    }

    public class OkaoUseFunction{
        public int useFunction[];
        public OkaoUseFunction(boolean body,boolean hand,boolean pet, boolean face,
                        boolean direction, boolean age, boolean gender,
                        boolean gaze, boolean blink, boolean expression,
                        boolean recognition){
            int arr[] = {body?1:0,hand?1:0,pet?1:0,face?1:0,direction?1:0,age?1:0,gender?1:0,
                    gaze?1:0,blink?1:0,expression?1:0,recognition?1:0};
            useFunction = arr;
        }
    }

    public interface OkaoExecuteListener {
        void onResult(OkaoResult result);
        void onError(ErrorStatus error);
    }

    public void okaoExecute(final OkaoUseFunction okaoUseFunction,final OkaoExecuteListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 顔検出・顔向き推定・年齢推定・性別推定・顔認証をON
                int useFunction[] = okaoUseFunction.useFunction;
                OkaoResult result = new OkaoResult();
                Int returnStatus = new Int();
                // 実行
                int ret = api.okaoExecute(useFunction, result, returnStatus);
                if (ret == ErrorCodes.HVCW_SUCCESS) {
                    final OkaoResult okaoResult = result;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(okaoResult);
                        }
                    });

                } else {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(new ErrorStatus(retval, retsts));
                        }
                    });
                }
            }
        }).start();
    }

    public interface RegisterAlbumListener {
        void onResult(int userID, int dataID, ResultDetection resultDetection, FileInfo fileInfo);
        void onError(int userID, int dataID, ErrorStatus error);
    }

    public void registerAlbum(final int userID, final int dataID, final RegisterAlbumListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ResultDetection resultDetection = new ResultDetection();
                FileInfo fileInfo = new FileInfo();
                Int returnStatus = new Int();
                // アルバム登録
                int ret = api.albumRegister(userID, dataID, resultDetection, fileInfo, returnStatus);
                final ResultDetection detection = resultDetection;
                final FileInfo info = fileInfo;
                if(ret == ErrorCodes.HVCW_SUCCESS) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(userID, dataID, detection, info);
                        }
                    });

                } else {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(userID, dataID, new ErrorStatus(retval, retsts));
                        }
                    });
                }
            }
        }).start();
    }

    public interface DeleteAlbumListener {
        void onResult(int userID, int dataID);
        void onError(int userID, int dataID, ErrorStatus error);
    }

    public void deleteAlbum(final int userID, final int dataID, final DeleteAlbumListener listener) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                Int returnStatus = new Int();
                // アルバムデータ削除
                int ret = api.albumDeleteData(userID, dataID, returnStatus);
                if(ret == ErrorCodes.HVCW_SUCCESS) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(userID, dataID);
                        }
                    });

                } else {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(userID, dataID, new ErrorStatus(retval, retsts));
                        }
                    });
                }

            }
        }).start();
    }

    public void disconnect() {
        if(isConnected == true) {
            api.disconnect();
        }
        api.deleteHandle();
        api = null;
    }

    public interface LastOkaoImageListener {
        void onResult(Bitmap bitmap);
        void onError(ErrorStatus error);
    }

    public void getLastOkaoImage(final LastOkaoImageListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Int imageBufSize = new Int();
                Int returnStatus = new Int();
                // 最新の OKAO 実行時の画像サイズを取得
                int ret = api.getLastOkaoImageSize(imageBufSize, returnStatus);

                if (ret != ErrorCodes.HVCW_SUCCESS) {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            listener.onError(new ErrorStatus(retval, retsts));
                        }
                    });
                    return;
                }

                byte[] image = new byte[imageBufSize.getIntValue()];
                // 最新の OKAO 実行時の画像取得
                ret = api.getLastOkaoImage(imageBufSize.getIntValue(), image, returnStatus);

                if (ret != ErrorCodes.HVCW_SUCCESS) {
                    final int retval = ret;
                    final int retsts = returnStatus.getIntValue();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                            listener.onError(new ErrorStatus(retval, retsts));
                        }
                    });
                    return;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_4444;
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    //noinspection deprecation
                    options.inPurgeable = true;
                }
                final Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onResult(bitmap);
                    }
                });
            }
        }).start();
    }
}
