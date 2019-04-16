Storage Access File System (SAFS)
====
A "backport" of the [java.nio.file](https://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html) packages.
It's primarily intended for Android ([safs-android](#safs-android)),
but its core package ([safs-core](#safs-core)) may work on any Java 6+ Unix/Linux JVM.

Some parts are unsupported or incomplete, mostly related to symlinks.

# safs-android
Android didn't get `java.nio.file` support until Android 8, so it will probably be quite some time before apps
supporting older Android can use it. When that time finally come it should be easy to switch over, simply changing `import`.

The major feature of `safs-android` is its ability to seamlessly use the Android 5.1+
[Storage Access Framework (SAF)](http://www.androiddocs.com/guide/topics/providers/document-provider.html)
when accessing _secondary external storage_ volumes, i.e. removable SD cards, once granted access by the user using an
[ACTION_OPEN_DOCUMENT_TREE](https://developer.android.com/reference/android/content/Intent.html#ACTION_OPEN_DOCUMENT_TREE) intent.
Android Q support is planned.

#### Possible benefits
* Use a more mature file-system API, i.e. [java.nio.file.Files](https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html).
* A unified API for accessing files on both internal, primary and secondary storage, from Android 4 through to today.
* An extra layer for working around Android issues.
* Makes it easier to "port" existing, or implement new, `FileSystemProvider`, e.g. for accessing zip files, FTP, Google Drive. 


For more information, see [safs-android](safs-android).

# safs-core
The core package use the [java.io.File](https://docs.oracle.com/javase/6/docs/api/index.html?java/io/File.html) to access the file-system.
Only support OS'es using `/` as path separator, e.g. Unix/Linux.

For more information, see [safs-core](safs-core).

