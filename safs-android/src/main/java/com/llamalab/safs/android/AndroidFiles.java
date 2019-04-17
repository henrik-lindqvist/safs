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

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import com.llamalab.safs.FileSystem;
import com.llamalab.safs.FileSystems;
import com.llamalab.safs.OpenOption;
import com.llamalab.safs.Path;
import com.llamalab.safs.ProviderMismatchException;
import com.llamalab.safs.spi.FileSystemProvider;
import com.llamalab.safs.unix.UnixPath;

import java.io.IOException;
import java.util.Iterator;

public final class AndroidFiles {

  private AndroidFiles () {}

  /**
   * @see android.content.Context#getCacheDir
   */
  public static Path getCacheDirectory () {
    return fileSystem().getCacheDirectory();
  }

  /**
   * @see android.os.Environment#getExternalStorageDirectory
   */
  public static Path getExternalStorageDirectory () {
    return fileSystem().getExternalStorageDirectory();
  }

  /**
   * @see android.content.Context#getFilesDir
   */
  public static Path getFilesDirectory () {
    final AndroidFileSystem fs = fileSystem();
    return fs.getPathSanitized(fs.getContext().getFilesDir().toString());
  }

  /**
   * @see android.content.Context#getDir
   */
  public static Path getDirectory (String name) {
    final AndroidFileSystem fs = fileSystem();
    return fs.getPathSanitized(fs.getContext().getDir(name, Context.MODE_PRIVATE).toString());
  }

  /**
   * @see android.os.Environment#getExternalStoragePublicDirectory
   */
  public static Path getExternalStoragePublicDirectory (String type) {
    return fileSystem().getPathSanitized(Environment.getExternalStoragePublicDirectory(type).toString());
  }

  public static Path getDataDirectory () {
    final AndroidFileSystem fs = fileSystem();
    return fs.getPathSanitized(fs.getContext().getApplicationInfo().dataDir);
  }

  /**
   * @see android.net.Uri#fromFile
   */
  public static Uri toUri (Path path) {
    final Uri.Builder ub = new Uri.Builder()
        .scheme(provider(path).getScheme())
        .encodedAuthority("");
    final Iterator<String> i = ((UnixPath)path).stringIterator();
    if (path.isAbsolute() || !i.hasNext())
      ub.appendEncodedPath("");
    while (i.hasNext())
      ub.appendPath(i.next());
    return ub.build();
  }

  /**
   * @see android.os.Environment#getExternalStorageState
   */
  public static boolean isExternalStorageMounted () throws IOException {
    return isFileStoreMounted(getExternalStorageDirectory());
  }

  /**
   * @see android.os.Environment#getExternalStorageState(java.io.File)
   */
  public static boolean isFileStoreMounted (Path path) throws IOException {
    return provider(path).isFileStoreMounted(path);
  }

  /**
   * @see android.os.Environment#isExternalStorageEmulated()
   */
  public static boolean isExternalStorageEmulated () throws IOException {
    return isFileStoreEmulated(getExternalStorageDirectory());
  }

  /**
   * @see android.os.Environment#isExternalStorageEmulated(java.io.File)
   */
  public static boolean isFileStoreEmulated (Path path) throws IOException {
    return provider(path).isFileStoreEmulated(path);
  }

  /**
   * @see android.os.Environment#isExternalStorageRemovable
   */
  public static boolean isExternalStorageRemovable () throws IOException {
    return isFileStoreRemovable(getExternalStorageDirectory());
  }

  /**
   * @see android.os.Environment#isExternalStorageRemovable(java.io.File)
   */
  public static boolean isFileStoreRemovable (Path path) throws IOException {
    return provider(path).isFileStoreRemovable(path);
  }

  public static ParcelFileDescriptor newParcelFileDescriptor (Path path, OpenOption... options) throws IOException {
    return provider(path).newParcelFileDescriptor(path, options);
  }

  private static AndroidFileSystemProvider provider (Path path) {
    final FileSystemProvider provider = path.getFileSystem().provider();
    if (!(provider instanceof AndroidFileSystemProvider))
      throw new ProviderMismatchException();
    return (AndroidFileSystemProvider)provider;
  }

  private static AndroidFileSystem fileSystem () {
    final FileSystem fs = FileSystems.getDefault();
    if (!(fs instanceof AndroidFileSystem))
      throw new ProviderMismatchException("AndroidFileSystem not default");
    return (AndroidFileSystem)fs;
  }
}
