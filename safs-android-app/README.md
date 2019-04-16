safs-android-app
================
An package for Android integration testing, see [AndroidFileSystemTests.java](src/androidTest/java/com/llamalab/safs/android/app/AndroidFileSystemTests.java).

### Known limitations
There's sadly some Android issues which `safs-android` can't work around: 
* [SAF](http://www.androiddocs.com/guide/topics/providers/document-provider.html) trims whitespace and "filter" certain characters in filenames,
  an `FileAlreadyExistsException` is thrown if that occur when creating a file. 
* Volumes/mounts not using `sdcardfs` are unable to change file last-modified time, so don't rely on 
 [StandardCopyOption.COPY_ATTRIBUTES](https://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardCopyOption.html#COPY_ATTRIBUTES).
 See [report](https://code.google.com/p/android/issues/detail?id=18624).
* [WatchService](https://docs.oracle.com/javase/7/docs/api/index.html?java/nio/file/WatchService.html), 
  which reply on [FileObserver](https://developer.android.com/reference/android/os/FileObserver), doesn't work through 
  [SAF](http://www.androiddocs.com/guide/topics/providers/document-provider.html). 
