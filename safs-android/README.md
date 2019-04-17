safs-android
============

[![Download](https://api.bintray.com/packages/hlindqvi/safs/safs/images/download.svg)](https://bintray.com/hlindqvi/safs/safs/_latestVersion)


### Known limitations
There's sadly some Android issues which can't be worked around: 
* [SAF](http://www.androiddocs.com/guide/topics/providers/document-provider.html) trim prefix/suffix whitespace and "filter" certain characters in filenames,
  an `FileAlreadyExistsException` is thrown if that occur when creating a file. 
* Volumes/mounts not using `sdcardfs` are unable to change file last-modified time, so don't rely on 
 [StandardCopyOption.COPY_ATTRIBUTES](https://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardCopyOption.html#COPY_ATTRIBUTES), 
 [Files.setLastModifiedTime()](https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#setLastModifiedTime(java.nio.file.Path,%20java.nio.file.attribute.FileTime))
 nor [Files.html.setAttribute()](https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#setAttribute(java.nio.file.Path,%20java.lang.String,%20java.lang.Object,%20java.nio.file.LinkOption...))
 See [report](https://code.google.com/p/android/issues/detail?id=18624).
* [WatchService](https://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html), 
  which use [FileObserver](https://developer.android.com/reference/android/os/FileObserver), doesn't seem to work on 
  _secondary external storage_ when accessed through 
  [SAF](http://www.androiddocs.com/guide/topics/providers/document-provider.html). 

See [safs-android-app](../safs-android-app) for integration tests.

### Getting started
Add to Gradle project `dependencies`:
```groovy
implementation 'com.llamalab.safs:safs-android:0.2.0'
```

Add to `AndroidManifest.xml`:
```xml-fragment
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Add to `proguard-rules.pro`:
````proguard
-keep class com.llamalab.safs.spi.FileSystemProvider { *; }
-keep class * extends com.llamalab.safs.spi.FileSystemProvider { *; }
-keep class * extends com.llamalab.safs.spi.FileTypeDetector { *; }
````


### Usage
Same as [java.nio.file](https://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html) packages except located in `com.llamalab.safs`, 
and instead of [java.nio.channels.SeekableByteChannel](https://docs.oracle.com/javase/7/docs/api/java/nio/channels/SeekableByteChannel.html)
use `com.llamalab.safs.channels.SeekableByteChannel`.

In addition to the standard [Files](https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html)
there's [com.llamalab.safs.android.AndroidFiles](src/main/java/com/llamalab/safs/android/AndroidFiles.java) with some convenience methods, and
[com.llamalab.safs.android.AndroidWatchEventKinds](src/main/java/com/llamalab/safs/android/AndroidWatchEventKinds.java) with Android specific
[WatchEvent.Kind](https://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchEvent.Kind.html).

Before use, `safs-android` need to be told to use the `com.llamalab.safs.android.AndroidFileSystemProvider` as default, 
otherwise it will use the [safs-core](../safs-core) provider. 
It also need a reference to a `Context`, assigned once, so it's easiest to do early in `Application`.

```java
import com.llamalab.safs.FileSystems;
import com.llamalab.safs.android.AndroidFileSystem;

public class MyApplication extends Application {

  static {
    System.setProperty("com.llamalab.safs.spi.DefaultFileSystemProvider", AndroidFileSystemProvider.class.getName());
  }

  public void onCreate () {
    ((AndroidFileSystem)FileSystems.getDefault()).setContext(this);
    super.onCreate();
  }
}
```

Before accessing a _secondary external storage_ volume, i.e. removable SD card, on Android 5.1+, 
the app has to be granted permission to it by the user, then `safs-android` has to be informed.
That's done with a [ACTION_OPEN_DOCUMENT_TREE](https://developer.android.com/reference/android/content/Intent.html#ACTION_OPEN_DOCUMENT_TREE) intent.
```java
public class MyActivity extends Activity {

  private static final int REQUEST_CODE_OPEN_DOCUMENT = 1;

  @Override
  protected void onCreate (Bundle state) {
    super.onCreate(state);
    // Ask user to grant permission to an storage volume, or subfolder thereof
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE_OPEN_DOCUMENT);
  }

  @Override
  protected void onActivityResult (int requestCode, int resultCode, Intent resultIntent) {
    if (REQUEST_CODE_OPEN_DOCUMENT != requestCode)
      super.onActivityResult(requestCode, resultCode, resultIntent);
    else if (RESULT_OK == resultCode) {
      // Take permission grant
      ((AndroidFileSystem)FileSystems.getDefault()).takePersistableUriPermission(resultIntent);
    }
  }
}
```
