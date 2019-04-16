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
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.llamalab.safs.channels.SeekableByteChannel;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.spi.AbstractInterruptibleChannel;

// https://android.googlesource.com/platform/libcore/+/marshmallow-release/luni/src/main/java/java/nio/FileChannelImpl.java
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class SeekableByteChannelWrapper extends AbstractInterruptibleChannel implements SeekableByteChannel {

  private final ParcelFileDescriptor pfd;
  private final FileDescriptor fd;
  private final int mode;

  /*
  public SeekableByteChannelWrapper (ParcelFileDescriptor pfd, String mode) {
    this(pfd, ParcelFileDescriptor.parseMode(mode));
  }
  */

  public SeekableByteChannelWrapper (ParcelFileDescriptor pfd, int mode) {
    this.pfd = pfd;
    this.fd = pfd.getFileDescriptor();
    this.mode = mode;
  }

  private void checkOpen () throws ClosedChannelException {
    if (!isOpen())
      throw new ClosedChannelException();
  }

  private void checkReadable () {
    //if ((mode & OsConstants.O_ACCMODE) == OsConstants.O_WRONLY)
    if ((mode & ParcelFileDescriptor.MODE_READ_WRITE) == ParcelFileDescriptor.MODE_WRITE_ONLY)
      throw new NonReadableChannelException();
  }

  private void checkWritable () {
    //if ((mode & OsConstants.O_ACCMODE) == OsConstants.O_RDONLY)
    if ((mode & ParcelFileDescriptor.MODE_READ_WRITE) == ParcelFileDescriptor.MODE_READ_ONLY)
      throw new NonWritableChannelException();
  }

  @Override
  protected void implCloseChannel () throws IOException {
    pfd.close();
  }

  @Override
  public int read (ByteBuffer dst) throws IOException {
    checkOpen();
    checkReadable();
    final int position = dst.position();
    if (dst.limit() <= position)
      return 0;
    int bytesRead = 0;
    boolean completed = false;
    try {
      begin();
      bytesRead = Os.read(fd, dst);
      if (bytesRead == 0)
        bytesRead = -1;
      completed = true;
    }
    catch (ErrnoException e) {
      if (OsConstants.EAGAIN != e.errno) {
        //noinspection UnnecessaryInitCause
        throw (IOException)new IOException(e.getMessage()).initCause(e);
      }
      bytesRead = 0;
    }
    finally {
      end(completed && bytesRead >= 0);
    }
    // BUG: Lollipop doesn't update position
    if (bytesRead > 0)
      dst.position(position + bytesRead);
    return bytesRead;
  }

  @Override
  public int write (ByteBuffer src) throws IOException {
    checkOpen();
    checkWritable();
    final int position = src.position();
    if (src.limit() <= position)
      return 0;
    int bytesWritten;
    boolean completed = false;
    try {
      begin();
      try {
        bytesWritten = Os.write(fd, src);
      }
      catch (ErrnoException e) {
        //noinspection UnnecessaryInitCause
        throw (IOException)new IOException(e.getMessage()).initCause(e);
      }
      completed = true;
    }
    finally {
      end(completed);
    }
    // BUG: Lollipop doesn't update position
    if (bytesWritten > 0)
      src.position(position + bytesWritten);
    return bytesWritten;
  }

  @Override
  public long position () throws IOException {
    checkOpen();
    try {
      return Os.lseek(fd, 0L, OsConstants.SEEK_CUR);
    }
    catch (ErrnoException e) {
      //noinspection UnnecessaryInitCause
      throw (IOException)new IOException(e.getMessage()).initCause(e);
    }
  }

  @Override
  public SeekableByteChannel position (long newPosition) throws IOException {
    if (newPosition < 0)
      throw new IllegalArgumentException();
    checkOpen();
    try {
      Os.lseek(fd, newPosition, OsConstants.SEEK_SET);
    }
    catch (ErrnoException e) {
      //noinspection UnnecessaryInitCause
      throw (IOException)new IOException(e.getMessage()).initCause(e);
    }
    return this;
  }

  @Override
  public long size () throws IOException {
    checkOpen();
    try {
      return Os.fstat(fd).st_size;
    }
    catch (ErrnoException e) {
      //noinspection UnnecessaryInitCause
      throw (IOException)new IOException(e.getMessage()).initCause(e);
    }
  }

  @Override
  public SeekableByteChannel truncate (long size) throws IOException {
    if (size < 0)
      throw new IllegalArgumentException();
    checkOpen();
    checkWritable();
    if (size < size()) {
      try {
        Os.ftruncate(fd, size);
      }
      catch (ErrnoException e) {
        //noinspection UnnecessaryInitCause
        throw (IOException)new IOException(e.getMessage()).initCause(e);
      }
    }
    if (position() > size)
      position(size);
    return this;
  }

}
