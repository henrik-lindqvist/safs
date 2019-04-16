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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import com.llamalab.safs.FileStore;
import com.llamalab.safs.Path;
import com.llamalab.safs.WatchService;
import com.llamalab.safs.internal.PathDescender;
import com.llamalab.safs.internal.SegmentEntry;
import com.llamalab.safs.java.JavaFileSystem;
import com.llamalab.safs.spi.FileSystemProvider;
import com.llamalab.safs.unix.UnixPath;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Known issues with the Android file system:
 * <ul>
 * <li>Changing last-modified time isn't possible due to <a href="https://code.google.com/p/android/issues/detail?id=18624">Android bug</a>
 * and lack of support in SAF, don't rely on {@link com.llamalab.safs.StandardCopyOption#COPY_ATTRIBUTES},
 * {@link com.llamalab.safs.Files#setAttribute} or {@link com.llamalab.safs.Files#setLastModifiedTime}.</li>
 * </ul>
 */
@SuppressWarnings("JavaReflectionMemberAccess")
@SuppressLint("NewApi")
public final class AndroidFileSystem extends JavaFileSystem {

  // TODO: http://developer.android.com/preview/features/scoped-folder-access.html

  private static final String TAG = "AndroidFileSystem";

  private static final String SCHEME_CONTENT = ContentResolver.SCHEME_CONTENT;
  private static final String AUTHORITY_DOCUMENTS = "com.android.externalstorage.documents";
  private static final String PATH_TREE = "tree";
  private static final String PATH_DOCUMENT = "document";
  private static final String PATH_CHILDREN = "children";

  @SuppressLint("InlinedApi")
  private static final int FLAG_READ_WRITE_URI_PERMISSION = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
  @SuppressLint("InlinedApi")
  private static final int FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION = FLAG_READ_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

  @SuppressWarnings("CanBeFinal")
  private static Method StorageManager_getVolumeList;
  @SuppressWarnings("CanBeFinal")
  private static Method StorageManager_getVolumeState;
  @SuppressWarnings("CanBeFinal")
  private static Method StorageVolume_getPath;
  static {
    if (Build.VERSION_CODES.N > Build.VERSION.SDK_INT) {
      try {
        StorageManager_getVolumeList = StorageManager.class.getMethod("getVolumeList");
      }
      catch (Throwable t) {
        // ignore
      }
    }
    if (Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT) {
      try {
        StorageManager_getVolumeState = StorageManager.class.getMethod("getVolumeState", String.class);
      }
      catch (Throwable t) {
        // ignore
      }
    }
    try {
      // StorageVolume has always been there, but @hide.
      StorageVolume_getPath = StorageVolume.class.getMethod("getPath");
    }
    catch (Throwable t) {
      // ignore
    }
  }

  private final AtomicReference<Context> contextRef = new AtomicReference<>();
  private volatile Map<String,AndroidFileStore> documentStores;
  private volatile PermissionEntry permissions;
  protected volatile Path externalStorageDirectory;

  AndroidFileSystem (FileSystemProvider provider) {
    super(provider);
  }

  public Context getContext () {
    final Context context = contextRef.get();
    if (context == null)
      throw new IllegalStateException("Context not set");
    return context;
  }

  @SuppressWarnings("UnusedReturnValue")
  public AndroidFileSystem setContext (Context context) {
    context = context.getApplicationContext();
    if (contextRef.compareAndSet(null, context)) {
      final IntentFilter filter = new IntentFilter();
      //filter.addAction(Intent.ACTION_MEDIA_EJECT);
      filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
      filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
      filter.addAction(Intent.ACTION_MEDIA_REMOVED);
      filter.addDataScheme("file"); // MUST!
      context.registerReceiver(mediaReceiver, filter);
    }
    return this;
  }

  ContentResolver getContentResolver () {
    return getContext().getContentResolver();
  }

  @Override
  protected Path getPathSanitized (String path) {
    return new AndroidPath(this, path);
  }

  Path toDocumentPath (Path path) throws IOException {
    // TODO: what to use?
    //return path.toAbsolutePath().normalize();
    return getPathSanitized(path.toAbsolutePath().toFile().getCanonicalPath());
  }

  @Override
  public WatchService newWatchService () throws IOException {
    return new AndroidWatchService(this);
  }

  @Override
  public Path getCacheDirectory () {
    if (cacheDirectory == null)
      cacheDirectory = getPathSanitized(getContext().getCacheDir().toString());
    return cacheDirectory;
  }

  public Path getExternalStorageDirectory () {
    if (externalStorageDirectory == null)
      externalStorageDirectory = getPathSanitized(Environment.getExternalStorageDirectory().toString());
    return externalStorageDirectory;
  }

  @SuppressLint("InlinedApi")
  public boolean isReadable () {
    return Build.VERSION_CODES.KITKAT > Build.VERSION.SDK_INT
        || isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
  }

  @Override
  public boolean isReadOnly () {
    return !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE);
  }

  @SuppressLint("WrongConstant")
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void takePersistableUriPermission (Uri uri, int flags) {
    if (!isTreeUri(uri))
      throw new NotDocumentUriException(uri);
    flags &= FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION;
    if (flags != FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION)
      throw new IllegalArgumentException("flags");
    getContentResolver().takePersistableUriPermission(uri, flags & FLAG_READ_WRITE_URI_PERMISSION);
    permissions = null;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void takePersistableUriPermission (Intent intent) {
    takePersistableUriPermission(intent.getData(), intent.getFlags());
  }

  @SuppressLint("WrongConstant")
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void releasePersistableUriPermission (Uri uri, int flags) {
    if (!isTreeUri(uri))
      throw new NotDocumentUriException(uri);
    flags &= FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION;
    if (flags != FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION)
      throw new IllegalArgumentException("flags");
    getContentResolver().releasePersistableUriPermission(uri, flags & FLAG_READ_WRITE_URI_PERMISSION);
    permissions = null;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void releasePersistableUriPermission (Uri uri) {
    releasePersistableUriPermission(uri, FLAG_PERSISTABLE_READ_WRITE_URI_PERMISSION);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void releasePersistableUriPermission (Intent intent) {
    releasePersistableUriPermission(intent.getData(), intent.getFlags());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterable<FileStore> getFileStores () {
    return (Iterable<FileStore>)(Iterable<?>)getAndroidFileStores();
  }

  public Iterable<AndroidFileStore> getAndroidFileStores () {
    final AndroidFileStore[] stores = loadAndroidFileStores();
    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      documentStores = toMap(stores);
      permissions = null;
    }
    return Arrays.asList(stores);
  }

  private AndroidFileStore[] loadAndroidFileStores () {
    final Context context = getContext();
    try {
      final StorageManager manager = (StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
      final List<StorageVolume> volumes;
      if (Build.VERSION_CODES.N <= Build.VERSION.SDK_INT) {
        //noinspection ConstantConditions
        volumes = manager.getStorageVolumes();
      }
      else
        volumes = Arrays.asList((StorageVolume[])StorageManager_getVolumeList.invoke(manager));
      final int length = volumes.size();
      final AndroidFileStore[] stores = new AndroidFileStore[length];
      for (int index = 0; index < length; ++index) {
        final StorageVolume volume = volumes.get(index);
        final Path path = getPathSanitized((String)StorageVolume_getPath.invoke(volume));
        stores[index] = new StorageVolumeFileStore(path, volume);
      }
      return stores;
    }
    catch (Throwable t) {
      Log.e(TAG, "Volume failure", t);
    }
    return new AndroidFileStore[] { new PrimaryFileStore(getExternalStorageDirectory()) };
  }

  private Map<String,AndroidFileStore> toMap (AndroidFileStore[] stores) {
    final Map<String,AndroidFileStore> documentStores = new HashMap<>(stores.length);
    for (final AndroidFileStore store : stores) {
      final String uuid = store.uuid();
      if (uuid != null)
        documentStores.put(uuid, store);
      else if (store.isPrimary())
        documentStores.put(AndroidFileStore.PRIMARY_NAME, store);
    }
    return documentStores;
  }

  /**
   * AndroidFileStore that can be mapped to document URIs, includes any stores with UUID.
   */
  private Map<String,AndroidFileStore> getDocumentFileStores () {
    final Map<String,AndroidFileStore> documentStores = this.documentStores;
    if (documentStores != null)
      return documentStores;
    return this.documentStores = toMap(loadAndroidFileStores());
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private PermissionEntry getPermissionRoot () {
    final PermissionEntry permissions = this.permissions;
    if (permissions != null)
      return permissions;
    return this.permissions = loadUriPermissions();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private PermissionEntry loadUriPermissions () {
    final Map<String,AndroidFileStore> stores = getDocumentFileStores();
    final PermissionEntry root = new PermissionEntry(getRootDirectory(), null, null, false);
    // Private app data directories
    for (final AndroidFileStore store : stores.values()) {
      final Path path = store.path().resolve("Android/data/" + getContext().getPackageName());
      for (final PathDescender<PermissionEntry> d = root.descentor((UnixPath)path); d.hasNext(); ) {
        switch (d.next()) {
          case MISSING_DIRECTORY:
            d.set(new PermissionEntry(d.parent().path.resolve(d.segment()), store, null, false));
            break;
          case MISSING_FILE:
            d.set(new PermissionEntry(path, store, null, true));
            break;
          case FILE:
            d.entry().unprotected = true;
            break;
        }
      }
    }
    // Persisted URI permissions
    for (final UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
      final Uri uri = permission.getUri();
      if (SCHEME_CONTENT.equals(uri.getScheme()) && AUTHORITY_DOCUMENTS.equals(uri.getAuthority())) {
        final List<String> segments = uri.getPathSegments();
        if (segments.size() == 2 && PATH_TREE.equals(segments.get(0))) {
          final String id = segments.get(1);
          final int i = id.indexOf(':');
          if (i != -1) {
            final AndroidFileStore store = stores.get(id.substring(0, i));
            if (store != null) {
              final Path path = store.path().resolve(id.substring(i + 1));
              for (final PathDescender<PermissionEntry> d = root.descentor((UnixPath)path); d.hasNext(); ) {
                switch (d.next()) {
                  case MISSING_DIRECTORY:
                    d.set(new PermissionEntry(d.parent().path.resolve(d.segment()), store, null, false));
                    break;
                  case MISSING_FILE:
                    d.set(new PermissionEntry(path, store, permission, false));
                    break;
                  case FILE:
                    d.entry().permission = permission;
                    break;
                }
              }
            }
          }
        }
      }
    }
    return root;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public Path getPath (Uri documentUri) throws IOException {
    if (SCHEME_CONTENT.equals(documentUri.getScheme()) && AUTHORITY_DOCUMENTS.equals(documentUri.getAuthority())) {
      final List<String> segments = documentUri.getPathSegments();
      final int count = segments.size();
      if (count >= 2 && PATH_TREE.equals(segments.get(0))) {
        String id = segments.get(1);
        int i = id.indexOf(':');
        if (i != -1) {
          final String uuid = id.substring(0, i);
          final AndroidFileStore store = getDocumentFileStores().get(uuid);
          if (store == null)
            throw new FileStoreNotFoundException(uuid);
          if (count == 2)
            return store.path().resolve(id.substring(i + 1));
          if (count == 4 && PATH_DOCUMENT.equals(segments.get(2))) {
            id = segments.get(3);
            i = id.indexOf(':');
            if (i != -1 && store.name().equals(id.substring(0, i)))
              return store.path().resolve(id.substring(i + 1));
          }
        }
      }
    }
    throw new NotDocumentUriException(documentUri);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public Uri getTreeDocumentUri (Path path) throws IOException {
    if (!path.isAbsolute())
      throw new IllegalArgumentException("Path must be absolute");
    PermissionEntry shortest = null;
    for (final PathDescender<PermissionEntry> d = getPermissionRoot().descentor((UnixPath)path); d.hasNext(); ) {
      switch (d.next()) {
        case DIRECTORY:
        case FILE: {
          final PermissionEntry entry = d.entry();
          if (entry.unprotected)
            return null;
          if (shortest == null) {
            final UriPermission permission = entry.permission;
            if (permission != null && permission.isReadPermission() && permission.isWritePermission())
              shortest = entry;
          }
        }
      }
    }
    if (shortest == null || shortest.store.isPrimary())
      return null;
    return shortest.permission.getUri().buildUpon()
        .appendEncodedPath(PATH_DOCUMENT)
        .appendPath(shortest.store.name() + ":" + shortest.store.path().relativize(path))
        .build();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isTreeUri (Uri uri) {
    if (SCHEME_CONTENT.equals(uri.getScheme()) && AUTHORITY_DOCUMENTS.equals(uri.getAuthority())) {
      final List<String> segments = uri.getPathSegments();
      return segments.size() == 2 && PATH_TREE.equals(segments.get(0));
    }
    return false;
  }

  static Uri childrenOf (Uri dirUri) {
    return dirUri.buildUpon()
        .appendEncodedPath(PATH_CHILDREN)
        .build();
  }

  /**
   * Only use up to KITKAT/19!
   */
  String getVolumeState (Path path) throws IllegalAccessException, InvocationTargetException {
    final StorageManager manager = (StorageManager)getContext().getSystemService(Context.STORAGE_SERVICE);
    return (String)StorageManager_getVolumeState.invoke(manager, path.toString());
  }

  private boolean isPermissionGranted (String permission) {
    return PackageManager.PERMISSION_GRANTED == getContext().checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid());
  }

  private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      documentStores = null;
      permissions = null;
    }
  };

  private static final class PermissionEntry extends SegmentEntry<PermissionEntry> {

    public final Path path;
    public final AndroidFileStore store;
    public UriPermission permission;
    public boolean unprotected;

    public PermissionEntry (Path path, AndroidFileStore store, UriPermission permission, boolean unprotected) {
      this.path = path;
      this.store = store;
      this.permission = permission;
      this.unprotected = unprotected;
    }

  } // class PermissionEntry
}
