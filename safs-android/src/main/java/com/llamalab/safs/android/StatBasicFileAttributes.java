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

import android.annotation.TargetApi;
import android.os.Build;
import android.system.OsConstants;
import android.system.StructStat;

import com.llamalab.safs.attributes.BasicFileAttributes;
import com.llamalab.safs.attributes.FileTime;
import com.llamalab.safs.internal.Utils;

import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class StatBasicFileAttributes implements BasicFileAttributes {

  private final StructStat stat;
  private volatile FileTime lastModifiedTime;
  private volatile FileTime lastAccessTime;

  public StatBasicFileAttributes (StructStat stat) {
    this.stat = stat;
  }

  @Override
  public Object fileKey () {
    return stat.st_ino;
  }

  @Override
  public boolean isDirectory () {
    return OsConstants.S_ISDIR(stat.st_mode);
  }

  @Override
  public boolean isOther () {
    //return !isDirectory() && !isRegularFile() && !isSymbolicLink();
    final int fmt = stat.st_mode & OsConstants.S_IFMT;
    return OsConstants.S_IFDIR != fmt
        && OsConstants.S_IFREG != fmt
        && OsConstants.S_IFLNK != fmt;
  }

  @Override
  public boolean isRegularFile () {
    return OsConstants.S_ISREG(stat.st_mode);
  }

  @Override
  public boolean isSymbolicLink () {
    return OsConstants.S_ISLNK(stat.st_mode);
  }

  @Override
  public long size () {
    return stat.st_size;
  }

  @Override
  public FileTime creationTime () {
    return Utils.ZERO_TIME;
  }

  @Override
  public FileTime lastModifiedTime () {
    if (lastModifiedTime == null)
      lastModifiedTime = FileTime.from(stat.st_mtime, TimeUnit.SECONDS);
    return lastModifiedTime;
  }

  @Override
  public FileTime lastAccessTime () {
    if (lastAccessTime == null)
      lastAccessTime = FileTime.from(stat.st_atime, TimeUnit.SECONDS);
    return lastAccessTime;
  }

}
