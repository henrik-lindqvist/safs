safs-core
=========

[![Download](https://api.bintray.com/packages/hlindqvi/safs/safs/images/download.svg)](https://bintray.com/hlindqvi/safs/safs/_latestVersion)

The core package use the [java.io.File](https://docs.oracle.com/javase/6/docs/api/java/io/File.html) to access the file-system.
Only support OS'es using `/` as path separator, e.g. Unix/Linux.

### Getting started
Add to Gradle project `dependencies`:
```groovy
implementation 'com.llamalab.safs:safs-core:0.2.0'
```

### Usage
Same as [java.nio.file](https://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html) packages except located in `com.llamalab.safs`, 
and instead of [java.nio.channels.SeekableByteChannel](https://docs.oracle.com/javase/7/docs/api/java/nio/channels/SeekableByteChannel.html)
use `com.llamalab.safs.channels.SeekableByteChannel`.
