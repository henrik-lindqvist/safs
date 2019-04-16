/*
 * Copyright (C) 2019 Henrik Lindqvist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.llamalab.safs.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.llamalab.safs.AccessDeniedException;
import com.llamalab.safs.AtomicMoveNotSupportedException;
import com.llamalab.safs.CopyOption;
import com.llamalab.safs.DirectoryNotEmptyException;
import com.llamalab.safs.DirectoryStream;
import com.llamalab.safs.FileAlreadyExistsException;
import com.llamalab.safs.FileStore;
import com.llamalab.safs.FileSystem;
import com.llamalab.safs.FileSystemAlreadyExistsException;
import com.llamalab.safs.FileSystemException;
import com.llamalab.safs.FileSystemNotFoundException;
import com.llamalab.safs.LinkOption;
import com.llamalab.safs.NoSuchFileException;
import com.llamalab.safs.NotDirectoryException;
import com.llamalab.safs.OpenOption;
import com.llamalab.safs.Path;
import com.llamalab.safs.StandardCopyOption;
import com.llamalab.safs.StandardOpenOption;
import com.llamalab.safs.attributes.BasicFileAttributes;
import com.llamalab.safs.attributes.FileAttribute;
import com.llamalab.safs.attributes.FileTime;
import com.llamalab.safs.internal.AbstractDirectoryStream;
import com.llamalab.safs.internal.BasicFileAttributeValue;
import com.llamalab.safs.internal.CompleteBasicFileAttributes;
import com.llamalab.safs.internal.FileType;
import com.llamalab.safs.internal.SearchSet;
import com.llamalab.safs.internal.Utils;
import com.llamalab.safs.channels.SeekableByteChannel;
import com.llamalab.safs.java.JavaFileSystemProvider;
import com.llamalab.safs.spi.FileSystemProvider;
import com.llamalab.safs.unix.UnixPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/*
 * TODO: KitKat hack?
 * http://www.androidpolice.com/2014/04/06/external-blues-redux-apps-still-have-a-loophole-for-writing-to-the-sd-card-on-kitkat-but-for-how-long/
 * https://github.com/android/platform_packages_providers_mediaprovider/tree/kitkat-release/src/com/android/providers/media
 *
 * https://github.com/android/platform_frameworks_base/tree/master/packages/ExternalStorageProvider
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/com/android/internal/content/FileSystemProvider.java
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/provider/DocumentsProvider.java
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/os/FileUtils.java
 *
 * https://github.com/jeisfeld/Augendiagnose/blob/master/AugendiagnoseLib/src/de/jeisfeld/augendiagnoselib/util/imagefile/FileUtil.java
 *
 * https://possiblemobile.com/2014/03/android-external-storage/
 * https://commonsware.com/blog/2014/04/09/storage-situation-removable-storage.html
 * http://developer.android.com/tools/help/mksdcard.html
 * http://www.chainfire.eu/articles/113/Is_Google_blocking_apps_writing_to_SD_cards_/
 * https://code.google.com/p/android/issues/detail?id=67570#c4444
 * https://groups.google.com/forum/#!topic/android-platform/14VUiIgwUjY[1-25]
 *
 *
 * TODO: Use ContentProviderClient instead, which is not thread-safe.
 */
public final class AndroidFileSystemProvider extends JavaFileSystemProvider {

  //private static final String TAG = "AndroidFileSystemProvider";
  //private static final boolean DEBUG = true || BuildConfig.DEBUG;

  @SuppressLint("InlinedApi")
  private static final String[] BASIC_NEW_DIRECTORY_STREAM_PROJECTION = {
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
  };
  private static final int BASIC_NEW_DIRECTORY_STREAM_COLUMN_DISPLAY_NAME = 0;

  @SuppressLint("InlinedApi")
  private static final String[] MIME_TYPE_PROJECTION = {
      DocumentsContract.Document.COLUMN_MIME_TYPE,
  };
  private static final int MIME_TYPE_COLUMN_MIME_TYPE = 0;

  @SuppressLint("InlinedApi")
  private static final String[] EXISTS_PROJECTION = {
      DocumentsContract.Document.COLUMN_DOCUMENT_ID
  };
  @SuppressWarnings("unused")
  private static final int EXISTS_PROJECTION_COLUMN_DOCUMENT_ID = 0;

  @SuppressLint("InlinedApi")
  private static final String[] BASIC_ATTRIBUTES_PROJECTION = {
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
  };
  private static final int BASIC_ATTRIBUTES_COLUMN_DOCUMENT_ID   = 0;
  private static final int BASIC_ATTRIBUTES_COLUMN_MIME_TYPE     = 1;
  private static final int BASIC_ATTRIBUTES_COLUMN_SIZE          = 2;
  private static final int BASIC_ATTRIBUTES_COLUMN_LAST_MODIFIED = 3;

  @SuppressWarnings("CanBeFinal")
  private static Class<?> ErrnoException_class;
  @SuppressWarnings("CanBeFinal")
  private static Field ErrnoException_errno;
  static {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT) {
      try {
        ErrnoException_class = Class.forName("libcore.io.ErrnoException");
        ErrnoException_errno = ErrnoException_class.getField("errno");
      }
      catch (Throwable t) {
        // never
      }
    }
  }

  private final String uriScheme;
  protected FileSystem fileSystem;

  /**
   * Called when included as non-default file system in the META-INF/services/com.llamalab.safs.spi.FileSystemProvider file.
   * URI scheme will be "android"
   */
  @SuppressWarnings("unused")
  public AndroidFileSystemProvider () {
    uriScheme = "android";
  }

  /**
   * Called when set as default file system in the "com.llamalab.safs.spi.DefaultFileSystemProvider" system property.
   * URI scheme will be "file"
   */
  @SuppressWarnings("unused")
  public AndroidFileSystemProvider (FileSystemProvider provider) {
    super(provider);
    uriScheme = "file";
    fileSystem = new AndroidFileSystem(this);
  }

  @Override
  protected Class<? extends UnixPath> getPathType () {
    return AndroidPath.class;
  }

  /**
   * @return "file" when used as default file system, "android" otherwise
   */
  @Override
  public String getScheme () {
    return uriScheme;
  }

  @Override
  public FileSystem getFileSystem (URI uri) {
    checkUri(uri);
    if (fileSystem == null)
      throw new FileSystemNotFoundException();
    return fileSystem;
  }

  @Override
  public FileSystem newFileSystem (URI uri, Map<String,?> env) throws IOException {
    checkUri(uri);
    return newFileSystem((Path)null, env);
  }

  @Override
  public FileSystem newFileSystem (Path path, Map<String,?> env) throws IOException {
    synchronized (this) {
      if (fileSystem != null)
        throw new FileSystemAlreadyExistsException();
      fileSystem = new AndroidFileSystem(this);
    }
    return fileSystem;
  }

  /**
   * @throws FileStoreNotFoundException if the file isn't found.
   */
  @Override
  public FileStore getFileStore (Path path) throws IOException {
    checkPath(path);
    path = path.toRealPath();
    for (final FileStore store : path.getFileSystem().getFileStores()) {
      if (path.startsWith(((AndroidFileStore)store).path()))
        return store;
    }
    throw new FileStoreNotFoundException(path.toString());
  }

  boolean isFileStoreMounted (Path path) throws IOException {
    final String state = getFileStoreState(path);
    return Environment.MEDIA_MOUNTED.equals(state)
        || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
  }

  boolean isFileStoreEmulated (Path path) throws IOException {
    checkPath(path);
    path = path.toRealPath();
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      return Environment.isExternalStorageEmulated(path.toFile());
    final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
    if (!path.toRealPath().startsWith(fs.getExternalStorageDirectory()))
      throw new IllegalArgumentException();
    return Environment.isExternalStorageEmulated();
  }

  boolean isFileStoreRemovable (Path path) throws IOException {
    checkPath(path);
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      return Environment.isExternalStorageRemovable(path.toFile());
    final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
    if (!path.toRealPath().startsWith(fs.getExternalStorageDirectory()))
      throw new IllegalArgumentException();
    return Environment.isExternalStorageRemovable();
  }

  private String getFileStoreState (Path path) throws IOException {
    checkPath(path);
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      return Environment.getExternalStorageState(path.toFile());
    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
      //noinspection deprecation
      return Environment.getStorageState(path.toFile());
    }
    final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
    try {
      return fs.getVolumeState(path);
    }
    catch (Throwable t) {
      if (!path.toRealPath().startsWith(fs.getExternalStorageDirectory()))
        throw new IllegalArgumentException();
      return Environment.getExternalStorageState();
    }
  }

  @Override
  public void createDirectory (Path dir, FileAttribute<?>... attrs) throws IOException {
    checkPath(dir);
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final AndroidFileSystem fs = (AndroidFileSystem)dir.getFileSystem();
      final UnixPath doc = (UnixPath)fs.toDocumentPath(dir);
      final Uri parentUri = fs.getTreeDocumentUri(doc.getParent());
      if (parentUri != null) {
        createDocument(fs, dir, parentUri, doc.getFileName().toString(), DocumentsContract.Document.MIME_TYPE_DIR);
        return;
      }
    }
    createDirectory(dir.toFile(), attrs);
  }

  @Override
  public void delete (Path path) throws IOException {
    checkPath(path);
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
      final Uri uri = fs.getTreeDocumentUri(fs.toDocumentPath(path));
      if (uri != null) {
        deleteDocument(fs, path, uri, false);
        return;
      }
    }
    delete(path.toFile(), false);
  }

  @Override
  public void copy (Path source, Path target, CopyOption... options) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      transfer(source, target, false, new SearchSet<>(options));
    else
      super.copy(source, target, options);
  }

  @Override
  public void move (Path source, Path target, CopyOption...options)throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      transfer(source, target, true, new SearchSet<>(options));
    else
      super.move(source, target, options);
  }

  // TODO: symbolic links
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void transfer (Path source, Path target, boolean move, Set<CopyOption> options) throws IOException {
    checkPath(source);
    checkPath(target);
    final AndroidFileSystem fs = (AndroidFileSystem)source.getFileSystem();
    final File sourceFile = source.toFile();
    final UnixPath sourceDoc = (UnixPath)fs.toDocumentPath(source);
    final Uri sourceUri = fs.getTreeDocumentUri(sourceDoc);
    final BasicFileAttributes sourceAttrs;
    if (sourceUri != null)
      sourceAttrs = readBasicFileAttributes(fs, source, sourceUri);
    else
      sourceAttrs = readBasicFileAttributes(sourceFile);
    final UnixPath targetDoc = (UnixPath)fs.toDocumentPath(target);
    if (sourceDoc.equals(targetDoc))
      return;
    final File targetFile = target.toFile();
    final String targetName = targetDoc.getFileName().toString();
    final Path targetParent = targetDoc.getParent();
    if (targetParent == null)
      throw new FileSystemException(target.toString(), null, "Target is root");
    // atomic
    if (move && options.contains(StandardCopyOption.ATOMIC_MOVE)) {
      if (sourceUri != null) {
        if (targetParent.equals(sourceDoc.getParent()) && renameDocument(fs, sourceUri, targetName) != null)
          return;
      }
      else {
        if (sourceFile.renameTo(targetFile))
          return;
      }
      throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Rename failed");
    }
    // delete target
    Uri targetUri = fs.getTreeDocumentUri(targetDoc);
    if (options.contains(StandardCopyOption.REPLACE_EXISTING)) {
      if (targetUri == null)
        delete(targetFile, true);
      else
        deleteDocument(fs, target, targetUri, true);
    }
    else {
      if ((targetUri != null) ? exists(fs, targetUri) : targetFile.exists())
        throw new FileAlreadyExistsException(target.toString());
    }
    // rename
    if (move) {
      if (sourceUri != null) {
        if (targetParent.equals(sourceDoc.getParent()) && renameDocument(fs, sourceUri, targetName) != null)
          return;
      }
      else {
        if (sourceFile.renameTo(targetFile))
          return;
      }
    }
    // transfer
    final Uri targetParentUri = fs.getTreeDocumentUri(targetParent);
    if (sourceAttrs.isDirectory()) {
      if (move && (sourceUri != null ? exists(fs, AndroidFileSystem.childrenOf(sourceUri)) : isNonEmptyDirectory(sourceFile)))
        throw new DirectoryNotEmptyException(source.toString());
      if (targetParentUri != null)
        targetUri = createDocument(fs, target, targetParentUri, targetName, DocumentsContract.Document.MIME_TYPE_DIR);
      else
        createDirectory(targetFile);
    }
    else {
      if (targetParentUri != null) {
        targetUri = createDocument(fs, target, targetParentUri, targetName, null);
        copyDocument(fs, sourceFile, targetFile, targetUri);
      }
      else
        copyFile(sourceFile, targetFile);
    }
    try {
      if (targetUri == null && options.contains(StandardCopyOption.COPY_ATTRIBUTES))
        setLastModifiedTime(targetFile, sourceAttrs.lastModifiedTime());
      if (move) {
        if (sourceUri != null)
          deleteDocument(fs, source, sourceUri, false);
        else
          delete(sourceFile, false);
      }
    }
    catch (IOException | RuntimeException e) {
      try {
        if (targetUri != null)
          deleteDocument(fs, target, targetUri, true);
        else
          delete(targetFile, true);
      }
      catch (Throwable t) {
        // ignore
      }
      throw e;
    }
  }

  @Override
  public InputStream newInputStream (Path path, OpenOption... options) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return super.newInputStream(path, options);
    return newInputStream(path, (options.length == 0) ? DEFAULT_NEW_INPUT_STREAM_OPTIONS : new SearchSet<>(options));
  }

  private InputStream newInputStream (Path path, Set<? extends OpenOption> options) throws IOException {
    if (options.contains(StandardOpenOption.WRITE))
      throw new IllegalArgumentException();
    return new ParcelFileDescriptor.AutoCloseInputStream(newParcelFileDescriptor(path, options));
  }

  @Override
  public OutputStream newOutputStream (Path path, OpenOption... options) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return super.newOutputStream(path, options);
    return newOutputStream(path, (options.length == 0) ? DEFAULT_NEW_OUTPUT_STREAM_OPTIONS : new SearchSet<>(options));
  }

  private OutputStream newOutputStream (Path path, Set<? extends OpenOption> options) throws IOException {
    if (!options.contains(StandardOpenOption.WRITE))
      throw new IllegalArgumentException();
    return new ParcelFileDescriptor.AutoCloseOutputStream(newParcelFileDescriptor(path, options));
  }

  @Override
  public SeekableByteChannel newByteChannel (Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return super.newByteChannel(path, options, attrs);
    return new SeekableByteChannelWrapper(newParcelFileDescriptor(path, options), toModeFlags(options));
  }

  public ParcelFileDescriptor newParcelFileDescriptor (Path path, OpenOption...options) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT) {
      checkPath(path);
      return newParcelFileDescriptor(path.toFile(), new SearchSet<>(options));
    }
    return newParcelFileDescriptor(path, new SearchSet<>(options));
  }

  // TODO: LinkOption.NOFOLLOW_LINKS
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private ParcelFileDescriptor newParcelFileDescriptor (Path path, Set<? extends OpenOption> options) throws IOException {
    checkPath(path);
    try {
      if (options.contains(AndroidOpenOption.NODOCUMENT)) {
        // non-document
        return openParcelFileDescriptor(path.toFile(), options);
      }
      final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
      final UnixPath doc = (UnixPath)fs.toDocumentPath(path);
      Uri uri = fs.getTreeDocumentUri(doc);
      if (uri == null) {
        // non-document
        return openParcelFileDescriptor(path.toFile(), options);
      }
      // document
      if (options.contains(StandardOpenOption.WRITE)) {
        if (exists(fs, uri)) {
          if (options.contains(StandardOpenOption.CREATE_NEW))
            throw new FileAlreadyExistsException(path.toString());
        }
        else {
          if (!options.contains(StandardOpenOption.CREATE_NEW) && !options.contains(StandardOpenOption.CREATE))
            throw new NoSuchFileException(path.toString());
          final Uri parentUri = fs.getTreeDocumentUri(doc.getParent());
          if (parentUri == null)
            return ParcelFileDescriptor.open(path.toFile(), toModeFlags(options));
          uri = createDocument(fs, path, parentUri, doc.getFileName().toString(), null);
        }
      }
      try {
        return fs.getContentResolver().openFileDescriptor(uri, toModeString(options));
      }
      catch (RuntimeException e) {
        // BUG: DocumentProvider throws undocumented exceptions.
        throw new NoSuchFileException(path.toString());
      }
    }
    catch (IOException e) {
      throw toProperException(e, path.toString(), null);
    }
  }

  private ParcelFileDescriptor newParcelFileDescriptor (File file, Set<? extends OpenOption> options) throws IOException {
    try {
      return openParcelFileDescriptor(file, options);
    }
    catch (IOException e) {
      throw toProperException(e, file.toString(), null);
    }
  }

  /**
   * @throws IOException MUST pass through {@link #toProperException}!
   */
  private ParcelFileDescriptor openParcelFileDescriptor (File file, Set<? extends OpenOption> options) throws IOException {
    if (   options.contains(StandardOpenOption.WRITE)
        && options.contains(StandardOpenOption.CREATE_NEW)
        && file.exists())
      throw new FileAlreadyExistsException(file.toString());
    return ParcelFileDescriptor.open(file, toModeFlags(options));
  }

  private static int toModeFlags (Set<? extends OpenOption> options) {
    if (!options.contains(StandardOpenOption.WRITE))
      return ParcelFileDescriptor.MODE_READ_ONLY;
    int mode;
    if (!options.contains(StandardOpenOption.READ))
      mode = ParcelFileDescriptor.MODE_WRITE_ONLY;
    else
      mode = ParcelFileDescriptor.MODE_READ_WRITE;
    if (options.contains(StandardOpenOption.CREATE_NEW) || options.contains(StandardOpenOption.CREATE))
      mode |= ParcelFileDescriptor.MODE_CREATE;
    if (options.contains(StandardOpenOption.APPEND))
      mode |= ParcelFileDescriptor.MODE_APPEND;
    if (options.contains(StandardOpenOption.TRUNCATE_EXISTING))
      mode |= ParcelFileDescriptor.MODE_TRUNCATE;
    return mode;
  }

  private static String toModeString (Set<? extends OpenOption> options) {
    // r, w, wt, wa, rw, rwt
    if (!options.contains(StandardOpenOption.WRITE))
      return "r";
    if (!options.contains(StandardOpenOption.READ)) {
      if (options.contains(StandardOpenOption.TRUNCATE_EXISTING))
        return "wt";
      if (options.contains(StandardOpenOption.APPEND))
        return "wa";
      return "w";
    }
    if (options.contains(StandardOpenOption.TRUNCATE_EXISTING))
      return "rwt";
    if (options.contains(StandardOpenOption.APPEND))
      throw new IllegalArgumentException("READ, WRITE and APPEND unsupported for documents");
    return "rw";
  }

  @Override
  public Path readSymbolicLink (Path link) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return readSymbolicLink(link);
    checkPath(link);
    final AndroidFileSystem fs = (AndroidFileSystem)link.getFileSystem();
    try {
      return fs.getPath(Os.readlink(link.toString()));
    }
    catch (RuntimeException e) {
      // BUG: https://code.google.com/p/android/issues/detail?id=209129
      throw e;
    }
    catch (Exception e) {
      throw toProperException((ErrnoException)e, link.toString(), null);
    }
  }

  protected boolean isSymbolicLink (Path path) {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return super.isSymbolicLink(path);
    try {
      return OsConstants.S_ISLNK(Os.lstat(path.toString()).st_mode);
    }
    catch (RuntimeException e) {
      // BUG: https://code.google.com/p/android/issues/detail?id=209129
      throw e;
    }
    catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <A extends BasicFileAttributes> A readAttributes (Path path, Class<A> type, LinkOption... options) throws IOException {
    checkPath(path);
    if (BasicFileAttributes.class != type)
      throw new UnsupportedOperationException("Unsupported type: "+type);
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
      final Uri uri = fs.getTreeDocumentUri(fs.toDocumentPath(path));
      if (uri != null)
        return (A)readBasicFileAttributes(fs, path, uri, options);
      try {
        for (final LinkOption option : options) {
          if (LinkOption.NOFOLLOW_LINKS == option)
            return (A)new StatBasicFileAttributes(Os.lstat(path.toString()));
        }
        return (A)new StatBasicFileAttributes(Os.stat(path.toString()));
      }
      catch (RuntimeException e) {
        // BUG: https://code.google.com/p/android/issues/detail?id=209129
        throw e;
      }
      catch (Exception e) {
        throw toProperException((ErrnoException)e, path.toString(), null);
      }
    }
    return (A)readBasicFileAttributes(path.toFile(), options);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private BasicFileAttributes readBasicFileAttributes (AndroidFileSystem fs, Path path, Uri uri, LinkOption... options) throws IOException {
    final Cursor cursor;
    try {
      cursor = fs.getContentResolver().query(uri, BASIC_ATTRIBUTES_PROJECTION, null, null, null);
    }
    catch (RuntimeException e) {
      // BUG: DocumentProvider throws undocumented exceptions
      throw new NoSuchFileException(path.toString());
    }
    try {
      //noinspection ConstantConditions
      if (!cursor.moveToNext())
        throw new NoSuchFileException(path.toString());
      final FileType fileType;
      if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(BASIC_ATTRIBUTES_COLUMN_MIME_TYPE)))
        fileType = FileType.DIRECTORY;
      else
        fileType = FileType.REGULAR_FILE;
      return new CompleteBasicFileAttributes(
          cursor.getString(BASIC_ATTRIBUTES_COLUMN_DOCUMENT_ID),
          fileType,
          cursor.getLong(BASIC_ATTRIBUTES_COLUMN_SIZE),
          Utils.ZERO_TIME,
          FileTime.fromMillis(cursor.getLong(BASIC_ATTRIBUTES_COLUMN_LAST_MODIFIED)),
          Utils.ZERO_TIME);
    }
    finally {
      Utils.closeQuietly(cursor);
    }
  }

  protected void setAttributes (Path path, Set<? extends FileAttribute<?>> attrs, LinkOption... options) throws IOException {
    checkPath(path);
    for (final FileAttribute<?> attr : attrs) {
      if (attr instanceof BasicFileAttributeValue) {
        if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
          final AndroidFileSystem fs = (AndroidFileSystem)path.getFileSystem();
          if (fs.getTreeDocumentUri(fs.toDocumentPath(path)) != null)
            throw new UnsupportedOperationException("Document attributes are immutable");
          // TODO: http://man7.org/linux/man-pages/man3/futimes.3.html
        }
        switch (((BasicFileAttributeValue)attr).type()) {
          case lastModifiedTime:
            setLastModifiedTime(path.toFile(), (FileTime)attr.value());
            continue;
        }
      }
      throw new UnsupportedOperationException("Attribute: "+attr.name());
    }
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream (final Path dir, final DirectoryStream.Filter<? super Path> filter) throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT)
      return super.newDirectoryStream(dir, filter);
    checkPath(dir);
    if (filter == null)
      throw new NullPointerException("filter");
    final AndroidFileSystem fs = (AndroidFileSystem)dir.getFileSystem();
    final Uri uri = fs.getTreeDocumentUri(dir);
    if (uri == null)
      return super.newDirectoryStream(dir, filter);
    final Cursor cursor;
    try {
      cursor = fs.getContentResolver().query(AndroidFileSystem.childrenOf(uri), BASIC_NEW_DIRECTORY_STREAM_PROJECTION, null, null, null);
    }
    catch (RuntimeException e) {
      if (DocumentsContract.Document.MIME_TYPE_DIR.equals(getMimeType(fs, dir, uri)))
        throw new NotDirectoryException(dir.toString());
      throw new FileSystemException(dir.toString(), null, "Failed to list directory document");
    }
    return new AbstractDirectoryStream<Path>() {

      @Override
      protected Path advance () throws IOException {
        //noinspection ConstantConditions
        while (cursor.moveToNext()) {
          final String displayName = cursor.getString(BASIC_NEW_DIRECTORY_STREAM_COLUMN_DISPLAY_NAME);
          if (displayName != null && !displayName.isEmpty()) {
            final Path entry = dir.resolve(displayName);
            if (filter.accept(entry))
              return entry;
          }
        }
        return null;
      }

      @SuppressLint("NewApi")
      @Override
      protected void implCloseStream () throws IOException {
        Utils.closeQuietly(cursor);
      }
    };
  }

  @Override
  protected IOException toProperException (IOException ioe, String file, String otherFile) {
    final Throwable t = ioe.getCause();
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      if (t instanceof ErrnoException && OsConstants.EACCES == ((ErrnoException)t).errno)
        return new AccessDeniedException(file);
    }
    else {
      try {
        if (ErrnoException_class.isInstance(t) && 13 == ErrnoException_errno.getInt(t))
          return new AccessDeniedException(file);
      }
      catch (NullPointerException | IllegalAccessException e) {
        // never
      }
    }
    return super.toProperException(ioe, file, otherFile);
  }

  @SuppressWarnings("SameParameterValue")
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private IOException toProperException (ErrnoException ene, String file, String otherFile) {
    if (OsConstants.ENOENT == ene.errno)
      return new NoSuchFileException(file);
    if (OsConstants.EACCES == ene.errno)
      return new AccessDeniedException(file);
    //noinspection UnnecessaryInitCause
    return (IOException)new IOException(ene.getMessage()).initCause(ene);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private boolean exists (AndroidFileSystem fs, Uri uri) throws IOException {
    final Cursor cursor;
    try {
      cursor = fs.getContentResolver().query(uri, EXISTS_PROJECTION, null, null, null);
    }
    catch (IllegalArgumentException e) {
      // BUG: DocumentProviders throws undocumented exceptions
      return false;
    }
    try {
      //noinspection ConstantConditions
      return cursor.getCount() > 0;
    }
    finally {
      Utils.closeQuietly(cursor);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private String getMimeType (AndroidFileSystem fs, Path path, Uri uri) throws IOException {
    final Cursor cursor;
    try {
      cursor = fs.getContentResolver().query(uri, MIME_TYPE_PROJECTION, null, null, null);
    }
    catch (IllegalArgumentException e) {
      // BUG: DocumentProviders throws undocumented exceptions
      return null;
    }
    try {
      //noinspection ConstantConditions
      if (!cursor.moveToNext())
        return null;
      final String mimeType = cursor.getString(MIME_TYPE_COLUMN_MIME_TYPE);
      return (mimeType != null) ? mimeType : "application/octet-stream";
    }
    finally {
      Utils.closeQuietly(cursor);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private Uri createDocument (AndroidFileSystem fs, Path path, Uri parentUri, String name, String mimeType) throws IOException {
    //if (DEBUG) Log.d(TAG, "createDocument: path="+path+", parentUri="+parentUri+", name="+name);
    Uri uri;
    try {
      uri = DocumentsContract.createDocument(fs.getContentResolver(), parentUri, mimeType, name);
    }
    catch (RuntimeException e) {
      uri = null;
    }
    if (uri == null)
      throw new FileSystemException(path.toString(), null, "Failed to create document");
    final Path created = fs.getPath(uri);
    if (created != null && !isSameFile(path.toFile(), created.toFile())) {
      try {
        DocumentsContract.deleteDocument(fs.getContentResolver(), uri);
      }
      catch (RuntimeException e) {
        // BUG: DocumentProviders throws undocumented exceptions
      }
      throw new FileAlreadyExistsException(path.toString());
    }
    return uri;
  }

  /**
   * Ensured NOT to delete a non-empty directory, which DocumentsContract.deleteDocument does!
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void deleteDocument (AndroidFileSystem fs, Path path, Uri uri, boolean ifExists) throws IOException {
    final String mimeType = getMimeType(fs, path, uri);
    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) && exists(fs, AndroidFileSystem.childrenOf(uri)))
      throw new DirectoryNotEmptyException(path.toString());
    boolean success;
    try {
      success = DocumentsContract.deleteDocument(fs.getContentResolver(), uri);
    }
    catch (RuntimeException e) {
      // BUG: DocumentProviders throws undocumented exceptions
      success = false;
    }
    if (!success) {
      if (mimeType != null)
        throw new FileSystemException(path.toString(), null, "Failed to delete document");
      if (!ifExists)
        throw new NoSuchFileException(path.toString());
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private Uri renameDocument (AndroidFileSystem fs, Uri documentUri, String displayName) throws IOException {
    try {
      return DocumentsContract.renameDocument(fs.getContentResolver(), documentUri, displayName);
    }
    catch (RuntimeException e) {
      // BUG: DocumentProviders throws undocumented exceptions
      return null;
    }
  }

  private void copyDocument (AndroidFileSystem fs, File sourceFile, File targetFile, Uri targetUri) throws IOException {
    final InputStream in;
    try {
      in = new FileInputStream(sourceFile);
    }
    catch (IOException e) {
      throw toProperException(e, sourceFile.toString(), null);
    }
    try {
      final OutputStream out;
      try {
        out = fs.getContentResolver().openOutputStream(targetUri, "w");
      }
      catch (RuntimeException e) {
        // BUG: DocumentProviders throws undocumented exceptions
        throw new FileSystemException(targetFile.toString(), null, e.getMessage());
      }
      try {
        Utils.transfer(in, out);
      }
      finally {
        //noinspection ConstantConditions
        out.close();
      }
    }
    catch (IOException e) {
      throw toProperException(e, sourceFile.toString(), targetFile.toString());
    }
    finally {
      in.close();
    }
  }

}
