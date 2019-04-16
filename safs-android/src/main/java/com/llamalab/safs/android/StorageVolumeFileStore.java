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
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageVolume;

import com.llamalab.safs.Path;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("JavaReflectionMemberAccess")
@SuppressLint("NewApi")
final class StorageVolumeFileStore extends AndroidFileStore {

  // https://github.com/aosp-mirror/platform_frameworks_base/blob/ics-mr0-release/core/java/android/os/storage/StorageVolume.java
  private static final int PRIMARY_STORAGE_ID = 0x00010001;

  @SuppressWarnings("CanBeFinal")
  private static Method StorageVolume_getStorageId;
  static {
    if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT) {
      try {
        StorageVolume_getStorageId = StorageVolume.class.getMethod("getStorageId");
      }
      catch (Throwable t) {
        // ignore
      }
    }
  }

  private final Path path;
  private final StorageVolume volume;

  public StorageVolumeFileStore (Path path, StorageVolume volume) {
    this.path = path;
    this.volume = volume;
  }

  @Override
  public String name () {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      final String uuid = volume.getUuid();
      if (uuid != null)
        return uuid;
      if (volume.isPrimary())
        return PRIMARY_NAME;
    }
    else if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
      if (volume.isPrimary())
        return PRIMARY_NAME;
      final int storageId = getStorageId();
      if (storageId != -1)
        return Integer.toString(storageId);
    }
    else {
      final int storageId = getStorageId();
      if (PRIMARY_STORAGE_ID == storageId)
        return PRIMARY_NAME;
      if (storageId != -1)
        return Integer.toString(storageId);
    }
    return "unknown";
  }

  @Override
  public Path path () {
    return path;
  }

  /**
   * @return Volume UUID on LOLLIPOP/21 and above, if available, otherwise null.
   */
  @Override
  String uuid () {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      return volume.getUuid();
    else
      return null;
  }

  @Override
  public boolean isPrimary () {
    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT)
      return volume.isPrimary();
    else
      return PRIMARY_STORAGE_ID == getStorageId();
  }

  @Override
  public boolean isEmulated () {
    return volume.isEmulated();
  }

  @Override
  public boolean isRemovable () {
    return volume.isRemovable();
  }

  @Override
  public String state () {
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      return volume.getState();
    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
      //noinspection deprecation
      return Environment.getStorageState(path.toFile());
    }
    else {
      try {
        return ((AndroidFileSystem)path.getFileSystem()).getVolumeState(path);
      }
      catch (Throwable t) {
        return isPrimary() ? Environment.getExternalStorageState() : Environment.MEDIA_UNKNOWN;
      }
    }
  }

  private int getStorageId () {
    try {
      return (int)StorageVolume_getStorageId.invoke(volume);
    }
    catch (InvocationTargetException e) {
      throw (RuntimeException)e.getTargetException();
    }
    catch (IllegalAccessException e) {
      return -1;
    }
  }

}
