package com.example.androidsecondshell2;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.InMemoryDexClassLoader;

public class ProxyApplication extends Application {

    private static final String TAG = "demo";
    private static final String librarySearchPath = null;/* this.getApplicationInfo().sourceDir + “\lib" */
    private static final String dexPath = null;
    private static final String optimizedDirectory = null;

    @Override
    public void onCreate() {
        super.onCreate();
        replaceApplication();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        try {
            //读取Classes.dex文件
            byte[] shellDexData = readDexFromApk();
            Log.d(TAG, "成功从源APK中读取classes.dex");
            //从中分理出源dex文件
            ByteBuffer[] byteBuffers = splitSourceApkFromShellDex(shellDexData);
            Log.d(TAG, "成功分离出源dex集合");
            //配置加载源程序的动态环境,即替换mClassLoader
            replaceClassLoaderInLoadedApk(byteBuffers);
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

    }

    //内存dex存储到文件中（找错的时候用的）
//    private void writeIntoDexFile(byte[] singleDexData) throws IOException {
//        //写到文件里看看到底咋回事！！！
//        File dexfile = getDir("payload_dex", MODE_PRIVATE);
//        String dexFilePath = dexfile.getAbsolutePath();
//        String fileName = dexFilePath + File.separator + "source.dex";
//        File apkfile = new File(fileName);
//        try {
//            FileOutputStream apkfileOutputStream = new FileOutputStream(apkfile);
//            apkfileOutputStream.write(singleDexData);
//            apkfileOutputStream.close();
//        }catch (IOException e){
//            throw new IOException(e);
//        }
//    }

    //替换应用的application
    private void replaceApplication() {
        String appClassName = getSourceApplicationClassName();

        try{
            //获取ActivityThread实例的scurrentActivityThread字段
            Object sCurrentActivityThreadObj = RefInvoke.invokeMethod(
                    "android.app.ActivityThread",
                    "currentActivityThread",
                    new Class[]{},
                    null,
                    new Object[]{}
            );
            Log.d(TAG, "sCurrentActivityThreadObj: " + sCurrentActivityThreadObj);
            // 此时在onCreate()方法中可以通过mInitialApplication获取mLoadedApk，因为在这之前赋值过了
            Object mInitialApplicationObj = RefInvoke.getFieldObject(
                    "android.app.ActivityThread",
                    "mInitialApplication",
                    sCurrentActivityThreadObj
            );

            Object mLoadedApkObj = RefInvoke.getFieldObject(
                    "android.app.Application",
                    "mLoadedApk",
                    mInitialApplicationObj
            );
            Log.d(TAG, "mLoadedApkObj: " + mLoadedApkObj);

            //置mLoadedApk中的mApplication为null，这样才能调用makeApplication()
            RefInvoke.setFieldObject(
                    "android.app.LoadedApk",
                    "mApplication",
                    mLoadedApkObj,
                    null
            );

            /*调用makeApplication()前，需要删除ActivityThread实例的mAllApplications中的mApplication(当前应用Application)
            * 还需要将LoadedApk实例中的mApplicationInfo.className替换成appClassName
            * 需不需要改ActivityThread实例中的mBoundApplication.appinfo.className呢？
            * 试一试
            * 尝试过了，并不需要，仍可正常运行
            * */

            //将当前应用Application从ActivityThread实例中的mAllApplications中删除
            ArrayList<Application> mAllApplicationsObj = (ArrayList<Application>) RefInvoke.getFieldObject(
                    "android.app.ActivityThread",
                    "mAllApplications",
                    sCurrentActivityThreadObj
            );
            mAllApplicationsObj.remove(mInitialApplicationObj);
            Log.d(TAG, "mInitialApplication 从 mAllApplications 中移除成功");

            //获取LoadedApk的mApplicationInfo字段
            ApplicationInfo mApplicationInfoObj = (ApplicationInfo) RefInvoke.getFieldObject(
                    "android.app.LoadedApk",
                    "mApplicationInfo",
                    mLoadedApkObj
            );
            Log.d(TAG, "LoadedApk的mApplicationInfo字段: " + mApplicationInfoObj);
            //替换className
            mApplicationInfoObj.className = appClassName;
            Log.d(TAG, "要加载的源程序application类为: " + appClassName);

            //反射调用makeApplication方法创建源程序的application
            Application SourceApplicationObj = (Application) RefInvoke.invokeMethod(
                    "android.app.LoadedApk",
                    "makeApplication",
                    new Class[]{boolean.class, Instrumentation.class},
                    mLoadedApkObj,
                    new Object[]{false, null}
            );
            Log.d(TAG, "创建源程序application成功");
            //将刚创建的Application设置到ActivityThread的mInitialApplication字段
            RefInvoke.setFieldObject(
                    "android.app.ActivityThread",
                    "mInitialApplication",
                    sCurrentActivityThreadObj,
                    SourceApplicationObj
            );
            Log.d(TAG, "源程序的application成功设置到mInitialApplication字段");


            //ContentProvider会持有代理的Application,需要特殊处理一下
            ArrayMap mProviderMapObj = (ArrayMap) RefInvoke.getFieldObject(
                    "android.app.ActivityThread",
                    "mProviderMap",
                    sCurrentActivityThreadObj
            );
            Log.d(TAG, "mProviderMapObj: " + mProviderMapObj.toString());
            //获取所有provider,装进迭代器中遍历
            Iterator iterator = mProviderMapObj.values().iterator();
            Log.d(TAG, "iterator: " + iterator);
            //开始遍历
            while(iterator.hasNext()){
                Object providerClientRecord = iterator.next();
                //获取ProviderClientRecord中的mLocalProvider字段
                Object mLocalProviderObj = RefInvoke.getFieldObject(
                        "android.app.ActivityThread$ProviderClientRecord",
                        "mLocalProvider",
                        providerClientRecord
                );
                //mLocalProviderObj可能为空
                if (mLocalProviderObj != null){
                    Log.d(TAG, "mLocalProviderObj: " + mLocalProviderObj);
                    //设置ContentProvider中的mContext字段为新建的Application
                    RefInvoke.setFieldObject(
                            "android.content.ContentProvider",
                            "mContext",
                            mLocalProviderObj,
                            SourceApplicationObj
                    );
                }

            }
            Log.d(TAG, "SourceApplication: " + SourceApplicationObj);
            //启动源程序
            SourceApplicationObj.onCreate();

        }catch (Exception e){
            Log.e(TAG, "replaceApplication: " + Log.getStackTraceString(e) );
        }

    }

    //获取在源程序xml中声明的源程序Application类
    private String getSourceApplicationClassName() {
        String appClassName = null;
        try {
            //获取AndroidManifest.xml 文件中的 <meta-data> 元素
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            //获取xml文件声明的Application类
            if (metaData != null && metaData.containsKey("APPLICATION_CLASS_NAME")){
                appClassName = metaData.getString("APPLICATION_CLASS_NAME");
            } else {
                Log.d(TAG, "xml文件中没有声明Application类名");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

        return appClassName;

    }

    //替换loadedApk中的mClassLoader
    private void replaceClassLoaderInLoadedApk(ByteBuffer[] byteBuffers) {
        //this.getClassLoader()方法获取的就是当前应用程序的mClassLoader
        //但是我们要替换mClassLoader，所以还是需要反射获取

        //获取ActivityThread实例的scurrentActivityThread字段
        Object sCurrentActivityThreadObj = RefInvoke.invokeMethod(
                "android.app.ActivityThread",
                "currentActivityThread",
                new Class[]{},
                null,
                new Object[]{}
        );
        Log.d(TAG, "sCurrentActivityThreadObj: " + sCurrentActivityThreadObj);
        /* 获取loadedApk实例,方法也有好几种(mInitialApplication mAllApplications mPackages)
            这里就根据mInitialApplication的mLoadedApk获取
         */
        Object mBoundApplicationObj = RefInvoke.getFieldObject("android.app.ActivityThread",
                "mBoundApplication",
                sCurrentActivityThreadObj
        );

        /*！！！！！
        这里出问题了，报错NullPointerException
        靠！
        此时还没返回到ActivityThread.handleBindApplication()中，也就是说下一步的mInitialApplication = app;还没有进行
        所以不能通过mInitialApplication获取mLoadedApk
        已修改！！！
        */
        Object mLoadedApkObj = RefInvoke.getFieldObject("android.app.ActivityThread$AppBindData",
                "info",
                mBoundApplicationObj
        );
        Log.d(TAG, "mLoadedApkObj: " + mLoadedApkObj);
        //获取loadedApk实例中的mClassLoader
        ClassLoader mClassLoader = (ClassLoader)RefInvoke.getFieldObject("android.app.LoadedApk",
                "mClassLoader",
                mLoadedApkObj
        );
        Log.d(TAG, "mClassLoader: " + mClassLoader);
        //创建InMemoryDexClassLoader,以当前类加载器为父类加载器，这样就不会丢失之前加载源APK中的资源
        /*
        待修改！！！！
        * */

        MyClassLoader inMemoryDexClassLoader = new MyClassLoader(this, byteBuffers, librarySearchPath , mClassLoader, dexPath, optimizedDirectory);
        Log.d(TAG, "inMemoryDexClassLoader: " + inMemoryDexClassLoader);
        //替换loadedApk实例中的mClassLoader字段
        RefInvoke.setFieldObject("android.app.LoadedApk",
                "mClassLoader",
                mLoadedApkObj,
                inMemoryDexClassLoader
        );
        Log.d(TAG, "replaceClassLoaderInLoadedApk: successes");

        //加载源程序的类
        //可有可无，只是测试看看有没有这个类
        try{
            inMemoryDexClassLoader.loadClass("com.example.sourceapk.MainActivity");
            Log.d(TAG, "com.example.sourceapk.MainActivity: 类加载成功");
        }catch (ClassNotFoundException e){
            Log.d(TAG, "com.example.sourceapk.MainActivity: " + Log.getStackTraceString(e));
        }
    }

    //从合并后的程序apk中提取dex文件（壳dex）
    private byte[] readDexFromApk() throws IOException {
        //获取当前应用程序的源码路径(apk),一般是data/app目录下,该目录用于存放用户安装的软件
        String sourceDir = this.getApplicationInfo().sourceDir;
        Log.d(TAG, "this.getApplicationInfo().sourceDir: " + this.getApplicationInfo().sourceDir);
        FileInputStream fileInputStream = new FileInputStream(sourceDir);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        ZipInputStream zipInputStream = new ZipInputStream(bufferedInputStream);
        //用于存放读取到的dex文件
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        while(true){
            //获取apk中的一个个文件
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            //遍历完了apk中的文件
            if (zipEntry == null){
                break;
            }
            // 提取出的壳程序的classes.dex文件,读取到bytearray中
            if (zipEntry.getName().equals("classes.dex")){
                byte[] bytes = new byte[1024];
                while(true){
                    //每次读取1024byte,返回的是读取到的byte数
                    int i = zipInputStream.read(bytes);
                    if (i == -1){
                        break;
                    }
                    //存放到开辟的byteArrayOutputStream中
                    byteArrayOutputStream.write(bytes,0, i);
                }
            }
            //关闭当前条目并定位到apk中的下一个文件
            zipInputStream.closeEntry();
        }
        zipInputStream.close();

        //返回读取到的dex文件
        return byteArrayOutputStream.toByteArray();
    }

    //从壳dex中分理出源dex集合，之后再分离出一个个单独的dex
    private ByteBuffer[] splitSourceApkFromShellDex(byte[] shellDexData) throws IOException {
        int shellDexlength = shellDexData.length;
        //开始解析dex文件
        byte[] sourceDexsSizeByte = new byte[4];
        //读取源dexs的大小
        System.arraycopy(shellDexData,shellDexlength - 4, sourceDexsSizeByte,0,4);
        //转成bytebuffer,方便4byte转int
        ByteBuffer wrap = ByteBuffer.wrap(sourceDexsSizeByte);
        //将byte转成int, 加壳时,长度我是按小端存储的
        int sourceDexsSizeInt = wrap.order(ByteOrder.LITTLE_ENDIAN).getInt();
        Log.d(TAG, "源dex集合的大小: " + sourceDexsSizeInt);
        //读取源dexs
        byte[] sourceDexsData = new byte[sourceDexsSizeInt];
        System.arraycopy(shellDexData,shellDexlength - sourceDexsSizeInt - 4, sourceDexsData, 0, sourceDexsSizeInt);
        //解密源dexs
        sourceDexsData = decryptoSourceApk(sourceDexsData);

        //更新部分
        //从源dexs中分离dex
        ArrayList<byte[]> sourceDexList = new ArrayList<>();
        int pos = 0;
        while(pos < sourceDexsSizeInt){
            //先提取四个字节，描述当前dex的大小
            //开始解析dex文件
            byte[] singleDexSizeByte = new byte[4];
            //读取源dexs的大小
            System.arraycopy(sourceDexsData, pos, singleDexSizeByte,0,4);
            //转成bytebuffer,方便4byte转int
            ByteBuffer singleDexwrap = ByteBuffer.wrap(singleDexSizeByte);
            int singleDexSizeInt = singleDexwrap.order(ByteOrder.LITTLE_ENDIAN).getInt();
            Log.d(TAG, "当前singleDex的大小: " + singleDexSizeInt);
            //读取单独dex
            byte[] singleDexData = new byte[singleDexSizeInt];
            System.arraycopy(sourceDexsData,pos + 4, singleDexData, 0, singleDexSizeInt);
            //加入到dexlist中
            sourceDexList.add(singleDexData);
            //更新pos
            pos += 4 + singleDexSizeInt;
        }

        //将dexlist包装成ByteBuffer
        int dexNum = sourceDexList.size();
        Log.d(TAG, "源dex的数量: " + dexNum);
        ByteBuffer[] dexBuffers = new ByteBuffer[dexNum];
        for (int i = 0; i < dexNum; i++){
            dexBuffers[i] = ByteBuffer.wrap(sourceDexList.get(i));
        }

        return dexBuffers;

        /*
        要不要分析源apk,取出so文件放入libPath目录中
        虽然InMemoryDexClassLoader的构造方法并不需要libpath参数，
        但是应用就是需要怎么办？

        解决方法：壳程序dex与源程序dex合并并以源程序APK为寄主，这样加载的时候资源、so等是能加载进来的，
        不过我们创建classloader时，要么以当前classloader为父加载器，要么合并当前classloader的elements
        */

    }

    // 解密源dex集合
    private byte[] decryptoSourceApk(byte[] sourceApkdata) {
        for (int i = 0; i < sourceApkdata.length; i++){
            sourceApkdata[i] ^= 0xff;
        }
        return sourceApkdata;

    }

}
