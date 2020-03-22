/**
 *
 */
package util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import activity.Splash101Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.lody.virtual.client.core.VirtualCore;

import download.ftp;


/**
 * @author byc
 *
 */
public class Crash101Application extends Application {
    //CrashHandler实例
    private static final String TAG=ConfigCt.TAG ;
    private Context mContext;
    private static CrashHandler current = null;
    private static Map<String, String>  infos=new HashMap<String, String>();
    @Override
    public void onCreate() {
        super.onCreate();
        mContext=getApplicationContext();

        loadPluginResources();
        ConfigCt.getInstance(mContext);
        getCrashHandler(getApplicationContext());
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            VirtualCore.get().startup(base);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 获取CrashHandler实例 ,单例模式 */
    public CrashHandler getCrashHandler(Context context) {
        if(current == null) {
            current = new CrashHandler(context);
        }
        return current;
    }

    /**
     * UncaughtException处理类,当程序发生Uncaught异常的时候,有该类来接管程序,并记录发送错误报告.
     *
     * @author user
     *
     */
    public class CrashHandler implements UncaughtExceptionHandler {

        public String TAG = "byc001";
        //系统默认的UncaughtException处理类
        private Thread.UncaughtExceptionHandler mDefaultHandler;
        //程序的Context对象
        private Context context;
        /** 保证只有一个CrashHandler实例 */
        private ftp mFtp;
        public CrashHandler(Context context) {
            this.context=context;
            TAG=ConfigCt.TAG;
            mFtp=ftp.getFtp(context);
            //获取系统默认的UncaughtException处理器
            mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
            //设置该CrashHandler为程序的默认处理器
            Thread.setDefaultUncaughtExceptionHandler(this);
        }
        /**
         * 当UncaughtException发生时会转入该函数来处理
         */
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            if (!handleException(ex) && mDefaultHandler != null) {
                //如果用户没有处理则让系统默认的异常处理器来处理
                mDefaultHandler.uncaughtException(thread, ex);
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "error : ", e);
                }
                //重新启动程序
                restartApp(context);
            }
        }

        /**
         * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
         *
         * @param ex
         * @return true:如果处理了该异常信息;否则返回false.
         */
        private boolean handleException(Throwable ex) {
            if (ex == null) {
                return false;
            }
            //使用Toast来显示异常信息
            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    //Toast.makeText(context, "很抱歉,程序出现异常,即将退出.", Toast.LENGTH_LONG).show();
                    Looper.loop();
                }
            }.start();
            ConfigCt.getInstance(mContext);
            //收集设备参数信息
            collectDeviceInfo(mContext);
            //保存日志文件
            String filename=saveCrashInfo2File(ex);
            //上传日志文件：
            if(filename!=null){
                mFtp=ftp.getFtp(context);
                mFtp.UploadStart(filename,false);
            }
            return true;
        }
        /*
         * 重新启动应用程序：
         */
        public void restartApp(Context context){
            //if(ConfigCt.appID.equals("ct"))Splash101Activity.setComponentEnabled(context, Splash101Activity.class, true);
            Intent intent = new Intent(context, Splash101Activity.class);// Intent.FLAG_ACTIVITY_NEW_TASK
            PendingIntent restartIntent = PendingIntent.getActivity(context, 0, intent,Intent.FLAG_ACTIVITY_NEW_TASK );
            AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000*5,restartIntent); // 1秒钟后重启应用
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
    /**
     * 收集设备参数信息
     * @param ctx
     */
    public void collectDeviceInfo(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = pi.versionCode + "";
                String androidVersion="Product Model: " + android.os.Build.MODEL + ","
                        +android.os.Build.BRAND+ ","
                        + android.os.Build.VERSION.SDK + ","
                        + android.os.Build.VERSION.RELEASE+ ","
                        +android.os.Build.VERSION.SDK_INT;
                infos.put("androidVersion", androidVersion);
                infos.put("versionName", versionName);
                infos.put("versionCode", versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "an error occured when collect package info", e);
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                infos.put(field.getName(), field.get(null).toString());
                Log.d(TAG, field.getName() + " : " + field.get(null));
            } catch (Exception e) {
                Log.e(TAG, "an error occured when collect crash info", e);
            }
        }
    }

    /**
     * 保存错误信息到文件中
     *
     * @param ex
     * @return  返回文件名称,便于将文件传送到服务器
     */
    private String saveCrashInfo2File(Throwable ex) {

        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append(key + "=" + value + "\r\n");
        }

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        Log.e(TAG, result);
        sb.append(result);
        try {
            //Config.LocalPath="/storage/sdcard1/byc/";
            //Log.i(Config.TAG, "Config.LocalPath ="+Config.LocalPath );
            long timestamp = System.currentTimeMillis();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
            String time = formatter.format(new Date());
            String fileName = ConfigCt.appID+"-err-" + time + "-" + timestamp + ".log";
            Funcs.makeDir(ConfigCt.LocalUploadPath);
            FileOutputStream fos = new FileOutputStream(ConfigCt.LocalUploadPath + fileName);
            fos.write(sb.toString().getBytes());
            fos.close();
            return fileName;
        } catch (Exception e) {
            Log.e(TAG, "an error occured while writing file...", e);
            return null;
        }

    }
    //以下是加载资源
    protected AssetManager mAssetManager;//资源管理器
    protected Resources mResources;//资源
    protected Theme mTheme;//主题
    protected String mPackageName;//主题
    protected void loadPluginResources() {
        File odex = this.getDir("payload_odex", MODE_PRIVATE);
        String apkFileName = odex.getAbsolutePath() + "/payload.apk";
        File dexFile = new File(apkFileName);
        if (dexFile.exists()){
            loadResources(apkFileName);
            Log.i(TAG, "plugin:"+apkFileName);
        }
    }
    protected void loadResources(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            mAssetManager = assetManager;
        } catch (Exception e) {
            Log.i("inject", "loadResource error:"+Log.getStackTraceString(e));
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        superRes.getDisplayMetrics();
        superRes.getConfiguration();
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(),superRes.getConfiguration());
        mTheme = mResources.newTheme();
        mTheme.setTo(super.getTheme());
        mPackageName=getPackageNameFromApkName(this,dexPath);
        Log.i("byc001", "mPackageName="+mPackageName);
    }

    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets() : mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources() : mResources;
    }

    @Override
    public Theme getTheme() {
        return mTheme == null ? super.getTheme() : mTheme;
    }
    public String getPluginPackageName() {
        return mPackageName == null ? super.getPackageName() : mPackageName;
    }
    /*
     * 从文件名获取包名：
     * @param context
     * @param filename  文件名
     * @return   PackageName：包名
     */
    public static String getPackageNameFromApkName(Context context,String filename) {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo =pm.getPackageArchiveInfo(filename, PackageManager.GET_ACTIVITIES);
        if (packageInfo != null) {
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            String packageName = appInfo.packageName;
            return packageName;
        }
        return null;
    }
}
