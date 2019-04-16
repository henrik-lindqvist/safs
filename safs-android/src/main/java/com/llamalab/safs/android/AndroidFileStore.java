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
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;

import com.llamalab.safs.AccessDeniedException;
import com.llamalab.safs.FileStore;
import com.llamalab.safs.Path;

import java.io.IOException;

public abstract class AndroidFileStore extends FileStore {

  static final String PRIMARY_NAME = "primary";

  @Override
  public boolean isReadOnly () {
    return Environment.MEDIA_MOUNTED_READ_ONLY.equals(state());
  }

  public abstract Path path ();
  public abstract String state ();

  public abstract boolean isPrimary ();
  public abstract boolean isEmulated ();
  public abstract boolean isRemovable ();

  public boolean isMounted () {
    final String state = state();
    return Environment.MEDIA_MOUNTED.equals(state)
        || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
  }

  @Override
  public final String type () {
    return null;
  }

  String uuid () {
    return null;
  }

  @Override
  public String toString () {
    return super.toString()+"[path="+path()+", name="+name()+", primary="+isPrimary()+"]";
  }

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  @Override
  public final long getTotalSpace () throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final StructStatVfs stat = newStructStatVfs();
      return stat.f_blocks * stat.f_bsize;
    }
    final StatFs stat = new StatFs(path().toString());
    if (Build.VERSION_CODES.JELLY_BEAN_MR2 <= Build.VERSION.SDK_INT)
      return stat.getTotalBytes();
    return (long)stat.getBlockCount() * stat.getBlockSize();
  }

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  @Override
  public final long getUsableSpace () throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final StructStatVfs stat = newStructStatVfs();
      return stat.f_bavail * stat.f_bsize;
    }
    final StatFs stat = new StatFs(path().toString());
    if (Build.VERSION_CODES.JELLY_BEAN_MR2 <= Build.VERSION.SDK_INT)
      return stat.getAvailableBytes();
    return (long)stat.getAvailableBlocks() * stat.getBlockSize();
  }

  @SuppressWarnings("deprecation")
  @SuppressLint("NewApi")
  @Override
  public final long getUnallocatedSpace () throws IOException {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final StructStatVfs stat = newStructStatVfs();
      return stat.f_bfree * stat.f_bsize;
    }
    final StatFs stat = new StatFs(path().toString());
    if (Build.VERSION_CODES.JELLY_BEAN_MR2 <= Build.VERSION.SDK_INT)
      return stat.getFreeBytes();
    return (long)stat.getFreeBlocks() * stat.getBlockSize();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private StructStatVfs newStructStatVfs () throws IOException {
    try {
      return Os.statvfs(path().toString());
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      // BUG: https://code.google.com/p/android/issues/detail?id=209129
      final ErrnoException ene = (ErrnoException)e;
      if (OsConstants.ENOENT == ene.errno)
        throw new FileStoreNotFoundException(name());
      if (OsConstants.EACCES == ene.errno)
        throw new AccessDeniedException(path().toString());
      //noinspection UnnecessaryInitCause
      throw (IOException)new IOException(ene.getMessage()).initCause(ene);
    }
  }

}
