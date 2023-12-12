# Android第二代加固壳 —— 不落地加载
Android第二代加固壳（不落地加载），为了支持源程序多dex的情况（请注意，这不是区别于第一壳的特征），加壳方法改为如下图所示：

![](https://raw.githubusercontent.com/gal2xy/blog_img/main/img/202312101629323.png)

将合并之后产生的dex放到源程序APK目录下。

由于Android系统版本原因，分为两种情况来实现：

1. Android 8以下 (ART环境下) 

   需要自定义类加载器，以及实现内存dex的加载。

2. Android 8及以上

   直接使用系统提供的类加载器InMemoryDexClassLoader。

------

由于本人还未学习过NDK开发，所以Native层遇到的问题暂时还未解决，故Android 8以下 (ART环境下) 的加壳仍存在问题。记录故障如下：

```
open libart.so success	//这是我用LOGE输出的
terminating with uncaught exception of type std::bad_alloc: std::bad_alloc
Fatal signal 6 (SIGABRT), code -6 in tid 27489 (ample.sourceapk)
Build fingerprint: 'google/marlin/marlin:7
...手机指纹...
```
