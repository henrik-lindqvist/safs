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

import android.os.FileObserver;

import com.llamalab.safs.Path;
import com.llamalab.safs.StandardWatchEventKinds;
import com.llamalab.safs.WatchEvent;

// http://linux.die.net/include/sys/inotify.h
public final class AndroidWatchEventKinds {

  /**
   * File was accessed.
   * @see android.os.FileObserver#ACCESS
   */
  public static final WatchEvent.Kind<Path> ACCESS = new AndroidWatchEventKind<>("ACCESS", Path.class, FileObserver.ACCESS);
  /**
   * File was modified.
   * @see android.os.FileObserver#MODIFY
   */
  public static final WatchEvent.Kind<Path> MODIFY = new AndroidWatchEventKind<>("MODIFY", Path.class, FileObserver.MODIFY);
  /**
   * Metadata changed.
   * @see android.os.FileObserver#ATTRIB
   */
  public static final WatchEvent.Kind<Path> ATTRIB = new AndroidWatchEventKind<>("ATTRIB", Path.class, FileObserver.ATTRIB);
  /**
   * File opened for writing was closed.
   * @see android.os.FileObserver#CLOSE_WRITE
   */
  public static final WatchEvent.Kind<Path> CLOSE_WRITE = new AndroidWatchEventKind<>("CLOSE_WRITE", Path.class, FileObserver.CLOSE_WRITE);
  /**
   * File not opened for writing was closed.
   * @see android.os.FileObserver#CLOSE_NOWRITE
   */
  public static final WatchEvent.Kind<Path> CLOSE_NOWRITE = new AndroidWatchEventKind<>("CLOSE_NOWRITE", Path.class, FileObserver.CLOSE_NOWRITE);
  /**
   * File was opened.
   * @see android.os.FileObserver#OPEN
   */
  public static final WatchEvent.Kind<Path> OPEN = new AndroidWatchEventKind<>("OPEN", Path.class, FileObserver.OPEN);
  /**
   * File moved out of watched directory.
   * @see android.os.FileObserver#MOVED_FROM
   */
  public static final WatchEvent.Kind<Path> MOVED_FROM = new AndroidWatchEventKind<>("MOVED_FROM", Path.class, FileObserver.MOVED_FROM);
  /**
   * File moved into watched directory
   * @see android.os.FileObserver#MOVED_TO
   */
  public static final WatchEvent.Kind<Path> MOVED_TO = new AndroidWatchEventKind<>("MOVED_TO", Path.class, FileObserver.MOVED_TO);
  /**
   * File/directory created in watched directory.
   * @see android.os.FileObserver#CREATE
   */
  public static final WatchEvent.Kind<Path> CREATE = new AndroidWatchEventKind<>("CREATE", Path.class, FileObserver.CREATE);
  /**
   * File/directory deleted from watched directory.
   * @see android.os.FileObserver#DELETE
   */
  public static final WatchEvent.Kind<Path> DELETE = new AndroidWatchEventKind<>("DELETE", Path.class, FileObserver.DELETE);
  /**
   * Watched file/directory was itself deleted.
   * @see android.os.FileObserver#DELETE_SELF
   */
  public static final WatchEvent.Kind<Void> DELETE_SELF = new AndroidWatchEventKind<>("DELETE_SELF", Void.class, FileObserver.DELETE_SELF);
  /**
   * Watched file/directory was itself moved.
   * @see android.os.FileObserver#MOVE_SELF
   */
  public static final WatchEvent.Kind<Void> MOVE_SELF = new AndroidWatchEventKind<>("MOVE_SELF", Void.class, FileObserver.MOVE_SELF);
  /**
   * File system containing watched object was unmounted.
   * Not officially supported by Android, may not work!
   */
  public static final WatchEvent.Kind<Void> UNMOUNT = new AndroidWatchEventKind<>("UNMOUNT", Void.class, 0x2000);
  /*
   * Watch was removed explicitly or automatically (file was deleted, or file system was unmounted).
   * Not officially supported by Android, may not work!
   */
  //public static final WatchEvent.Kind<Void> IGNORED = new AndroidWatchEventKind<Void>("IGNORED", Void.class, 0x8000);

  private static final int ALL_MASK = FileObserver.ALL_EVENTS | 0x2000;// | 0x8000;

  private static final WatchEvent.Kind<?>[] ALL_KINDS = {
      ACCESS, MODIFY, ATTRIB, CLOSE_WRITE, CLOSE_NOWRITE, OPEN, MOVED_FROM, MOVED_TO, CREATE, DELETE, DELETE_SELF, MOVE_SELF,
      null, UNMOUNT, //null, IGNORED
  };

  private AndroidWatchEventKinds () {}

  /**
   * {@link AndroidWatchEventKinds} of event mask.
   */
  public static WatchEvent.Kind<?>[] of (int mask) {
    mask &= ALL_MASK;
    int o = Integer.bitCount(mask);
    final WatchEvent.Kind<?>[] kinds = new WatchEvent.Kind<?>[o];
    for (int i = ALL_KINDS.length; --i >= 0;) {
      if ((mask & (1 << i)) != 0)
        kinds[--o] = ALL_KINDS[i];
    }
    return kinds;
  }

  /**
   * Event mask for {@link AndroidWatchEventKinds} or {@link StandardWatchEventKinds}.
   */
  public static int mask (WatchEvent.Kind kind) {
    if (kind instanceof AndroidWatchEventKind)
      return ((AndroidWatchEventKind)kind).event();
    if (StandardWatchEventKinds.ENTRY_CREATE == kind)
      return FileObserver.CREATE | FileObserver.MOVED_TO;
    if (StandardWatchEventKinds.ENTRY_DELETE == kind)
      return FileObserver.DELETE | FileObserver.MOVED_FROM;
    if (StandardWatchEventKinds.ENTRY_MODIFY == kind)
      return FileObserver.MODIFY | FileObserver.ATTRIB;
    return 0;
  }

  /**
   * Event mask for {@link AndroidWatchEventKinds} and {@link StandardWatchEventKinds}.
   */
  public static int mask (WatchEvent.Kind<?>... kinds) {
    int mask = 0;
    for (final WatchEvent.Kind<?> kind : kinds)
      mask |= mask(kind);
    return mask;
  }

}
