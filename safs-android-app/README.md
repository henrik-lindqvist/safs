safs-android-app
================

A package for Android integration testing of [safs-android](../safs-android).

Run [AndroidFileSystemTests.java](src/androidTest/java/com/llamalab/safs/android/app/AndroidFileSystemTests.java).


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
