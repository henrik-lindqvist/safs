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
import android.util.Log;

import com.llamalab.safs.Files;
import com.llamalab.safs.LinkOption;
import com.llamalab.safs.NoSuchFileException;
import com.llamalab.safs.Path;
import com.llamalab.safs.ProviderMismatchException;
import com.llamalab.safs.StandardWatchEventKinds;
import com.llamalab.safs.WatchEvent;
import com.llamalab.safs.Watchable;
import com.llamalab.safs.internal.AbstractWatchKey;
import com.llamalab.safs.internal.AbstractWatchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * BUG: https://code.google.com/p/android/issues/detail?id=92329
 */
final class AndroidWatchService extends AbstractWatchService {

  private static final String TAG = "AndroidWatchService";
  private static final boolean DEBUG = BuildConfig.DEBUG;

  private static final int IN_Q_OVERFLOW = 0x4000;

  private final static Map<Path,PathObserver> observers = new HashMap<>();

  private final AndroidFileSystem fs;

  AndroidWatchService (AndroidFileSystem fs) {
    this.fs = fs;
  }

  AbstractWatchKey register (AndroidPath path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
    if (!fs.equals(path.getFileSystem()))
      throw new ProviderMismatchException();
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      throw new NoSuchFileException(path.toString());
    final AndroidWatchKey key = new AndroidWatchKey(this, path, kinds);
    synchronized (observers) {
      PathObserver observer = observers.get(path);
      if (observer == null)
        observers.put(path, observer = new PathObserver(path));
      observer.register(key);
    }
    return key;
  }

  private void cancel (AndroidWatchKey key) {
    final Path path = (Path)key.watchable();
    synchronized (observers) {
      final PathObserver observer = observers.get(path);
      if (observer != null && !observer.cancel(key))
        observers.remove(path);
    }
  }

  @Override
  protected void implCloseService () throws IOException {
    synchronized (observers) {
      for (Iterator<PathObserver> i = observers.values().iterator(); i.hasNext();) {
        if (!i.next().cancel(this))
          i.remove();
      }
    }
  }

  private static final class PathObserver {

    // TODO: maybe make it a weak set?
    private final List<AndroidWatchKey> keys = new ArrayList<>();
    private final String path;
    private FileObserver observer;
    private int mask;

    public PathObserver (Path path) {
      this.path = path.toString();
    }

    public synchronized void register (AndroidWatchKey key) {
      keys.add(key);
      listen(mask | key.mask);
    }

    public synchronized boolean cancel (AndroidWatchKey key) {
      int mask = 0;
      for (Iterator<AndroidWatchKey> i = keys.iterator(); i.hasNext(); ) {
        final AndroidWatchKey k = i.next();
        if (key == k)
          i.remove();
        else
          mask |= k.mask;
      }
      return listen(mask);
    }

    public synchronized boolean cancel (AndroidWatchService service) {
      int mask = 0;
      for (Iterator<AndroidWatchKey> i = keys.iterator(); i.hasNext(); ) {
        final AndroidWatchKey k = i.next();
        if (service == k.service())
          i.remove();
        else
          mask |= k.mask;
      }
      return listen(mask);
    }

    private boolean listen (int newMask) {
      if (newMask == 0) {
        if (DEBUG) Log.d(TAG, "listen: stopping");
        mask = 0;
        observer.stopWatching();
        observer = null;
        return false;
      }
      if (newMask != mask) {
        if (DEBUG) Log.d(TAG, "listen: updating to 0x"+Integer.toHexString(newMask));
        observer = new FileObserver(path, mask = newMask) {
          @Override
          public void onEvent (int event, String path) {
            if (DEBUG) Log.d("PathObserver", "onEvent: 0x"+Integer.toHexString(event)+", "+path);
            synchronized (PathObserver.this) {
              for (final AndroidWatchKey key : keys)
                key.onEvent(event, path);
            }
          }
          @Override
          protected void finalize () {
            // Prevent stopWatching call!
          }
        };
        // RTFM Google: http://linux.die.net/man/7/inotify
        // startWatching will replace the previously started FileObserver for the same path, so
        // there's no need to call stopWatching. See the startWatching method in class ObserverThread:
        // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/FileObserver.java#L94
        observer.startWatching();
      }
      return true;
    }

  } // class PathObserver

  private static final class AndroidWatchKey extends AbstractWatchKey {

    private final WatchEvent.Kind<?>[] kinds;
    private volatile int mask;

    public AndroidWatchKey (AbstractWatchService service, Watchable watchable, WatchEvent.Kind<?>[] kinds) {
      super(service, watchable, 512);
      final int mask = AndroidWatchEventKinds.mask(kinds);
      if (mask == 0)
        throw new IllegalArgumentException("kinds");
      this.mask = mask;
      this.kinds = kinds;
    }

    @Override
    public boolean isValid () {
      return mask != 0 && super.isValid();
    }

    @Override
    public void cancel () {
      mask = 0;
      ((AndroidWatchService)service()).cancel(this);
    }

    @SuppressWarnings("unchecked")
    void onEvent (int event, String path) {
      if ((event & mask) != 0) {
        for (final WatchEvent.Kind<?> kind : kinds) {
          if ((event & AndroidWatchEventKinds.mask(kind)) != 0) {
            if (DEBUG) Log.d("AndroidWatchKey", "onEvent: "+kind+", "+path);
            if (Path.class == kind.type() && path != null)
              signalEvent((WatchEvent.Kind<Path>)kind, ((AndroidWatchService)service()).fs.getPath(path));
            else
              signalEvent(kind, null);
          }
        }
      }
      else if (IN_Q_OVERFLOW == event)
        signalEvent(StandardWatchEventKinds.OVERFLOW, null);
    }

  } // class AndroidWatchKey

}
