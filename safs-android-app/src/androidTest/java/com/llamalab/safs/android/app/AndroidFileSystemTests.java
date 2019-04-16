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

package com.llamalab.safs.android.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.test.SingleLaunchActivityTestCase;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import com.llamalab.safs.DirectoryNotEmptyException;
import com.llamalab.safs.DirectoryStream;
import com.llamalab.safs.FileAlreadyExistsException;
import com.llamalab.safs.FileSystems;
import com.llamalab.safs.Files;
import com.llamalab.safs.NoSuchFileException;
import com.llamalab.safs.Path;
import com.llamalab.safs.Paths;
import com.llamalab.safs.StandardOpenOption;
import com.llamalab.safs.WatchEvent;
import com.llamalab.safs.WatchKey;
import com.llamalab.safs.WatchService;
import com.llamalab.safs.android.AndroidFileStore;
import com.llamalab.safs.android.AndroidFileSystem;
import com.llamalab.safs.android.AndroidFileSystemProvider;
import com.llamalab.safs.android.AndroidFiles;
import com.llamalab.safs.android.AndroidWatchEventKinds;
import com.llamalab.safs.attributes.FileTime;
import com.llamalab.safs.internal.Utils;
import com.llamalab.safs.channels.SeekableByteChannel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Integration test.
 *
 * <p>Low level AVD's (pre KitKat) can be mis-configured causing AccessDeniedException, solution:</p>
 * <pre>chmod 777 /mnt/sdcard</pre>
 */
public class AndroidFileSystemTests extends SingleLaunchActivityTestCase<OpenDocumentTreeActivity> {

  private static final String TAG = "AndroidFileSystemTest";

  static {
    System.setProperty("com.llamalab.safs.spi.DefaultFileSystemProvider", AndroidFileSystemProvider.class.getName());
  }

  private AndroidFileSystem fs;
  private Path selected;

  public AndroidFileSystemTests () {
    super(BuildConfig.APPLICATION_ID, OpenDocumentTreeActivity.class);
  }

  public Context getContext () {
    return getActivity();
  }

  @Override
  protected void setUp () throws Exception {
    super.setUp();
    fs = (AndroidFileSystem)FileSystems.getDefault();
    fs.setContext(getContext());

    if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
      Intent resultIntent;
      for (;;) {
        final OpenDocumentTreeActivity activity = getActivity();
        if ((resultIntent = activity.resultIntent) != null)
          break;
        if (activity.isDestroyed())
          throw new IllegalStateException("Test aborted");
        Thread.sleep(1000);
      }
      fs.takePersistableUriPermission(resultIntent);
      //noinspection ConstantConditions
      selected = fs.getPath(resultIntent.getData());
    }
    else
      selected = fs.getExternalStorageDirectory();
    assertNotNull("selected", selected);
    Log.i(TAG, "selected: " + selected);
  }

  @Override
  protected void tearDown () throws Exception {
    super.tearDown();
    getActivity().finish();
  }

  public void testAndroidUri () throws Throwable {
    final Path path1 = Paths.get("/");
    assertEquals(Uri.fromFile(path1.toFile()), AndroidFiles.toUri(path1));
    final Path path2 = Paths.get("foo/bar");
    assertEquals(Uri.fromFile(path2.toFile()), AndroidFiles.toUri(path2));
    final Path path3 = Paths.get("/foo/bar");
    assertEquals(Uri.fromFile(path3.toFile()), AndroidFiles.toUri(path3));
    final Path path4 = Paths.get("");
    assertEquals(Uri.fromFile(path4.toFile()), AndroidFiles.toUri(path4));
  }

  /**
   * Will fail if a non-document path is selected.
   */
  @Suppress
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void testDocumentUri () throws Throwable {

    final Path path1 = selected.resolve("test1");
    final Uri uri1 = fs.getTreeDocumentUri(path1);
    assertNotNull(uri1);
    assertTrue(DocumentsContract.isDocumentUri(getContext(), uri1));
    assertEquals(path1, fs.getPath(uri1));

    final Path path2 = path1.resolve("test2");
    final Uri uri2 = fs.getTreeDocumentUri(path2);
    assertNotNull(uri2);
    assertTrue(DocumentsContract.isDocumentUri(getContext(), uri2));
    assertEquals(path2, fs.getPath(uri2));
  }

  /**
   * Will fail if a non-document path is selected.
   */
  public void testFilenameCharacters () throws Throwable {
    assertEquals("/a%23b", Uri.fromFile(new File("/a#b")).getEncodedPath());

    //final AndroidFileSystem fs = (AndroidFileSystem)FileSystems.getDefault();
    Log.i(TAG, "tree uri: "+fs.getTreeDocumentUri(selected.resolve("a#b")));

    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      assertTrue(Files.isDirectory(base));

      final Path a = Files.createFile(base.resolve("a.b"));
      assertTrue(a.toString(), Files.isRegularFile(a));

      final Path b = Files.createFile(base.resolve("a#b"));
      assertTrue(b.toString(), Files.isRegularFile(b));

      final Path c = Files.createFile(base.resolve("a%b"));
      assertTrue(c.toString(), Files.isRegularFile(c));

      try (final DirectoryStream<Path> ds = Files.newDirectoryStream(base)) {
        final Set<Path> actual = new HashSet<>();
        for (final Path path : ds)
          assertTrue("duplicate: "+path, actual.add(path));
        final Set<Path> expected = new HashSet<>(Arrays.asList(a, b, c));
        assertEquals("", expected, actual);
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testDelete () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      assertTrue(Files.isDirectory(base));

      final Path dir = base.resolve("dir");
      Files.createDirectory(dir);
      assertTrue(Files.isDirectory(dir));

      final Path file = dir.resolve("file");
      Files.createFile(file);
      assertTrue(Files.isRegularFile(file));

      try {
        Files.delete(dir);
        fail();
      }
      catch (DirectoryNotEmptyException e) {
        // expected
      }

      Files.delete(file);
      assertTrue(Files.notExists(file));

      Files.delete(dir);
      assertTrue(Files.notExists(dir));

      Files.deleteIfExists(file);

      try {
        Files.delete(dir);
        fail();
      }
      catch (NoSuchFileException e) {
        // expected
      }

      try {
        Files.delete(file);
        fail();
      }
      catch (NoSuchFileException e) {
        // expected
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testCreateDirectory () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      assertTrue(Files.isDirectory(base));

      final Path dir1 = base.resolve("dir1");
      Files.createDirectory(dir1);
      assertTrue(Files.isDirectory(dir1));

      try {
        Files.createDirectory(dir1);
        fail();
      }
      catch (FileAlreadyExistsException e) {
        // expected
      }
      final Path dir3 = dir1.resolve("dir2/dir3");
      Files.createDirectories(dir3);
      assertTrue(Files.isDirectory(dir3));

      final Path dir4 = dir1.resolve("./dir2/../dir2/dir3/dir4");
      Files.createDirectories(dir4);
      assertTrue(Files.isDirectory(dir4));

      Files.createDirectories(dir4);

      final Path dir5 = dir3.resolve("../dir5");
      Files.createDirectories(dir5);
      assertTrue(Files.isDirectory(dir5));
      assertTrue(Files.isSameFile(dir5, dir1.resolve("dir2/dir5")));

      final Path file = dir1.resolve("file");
      Files.createFile(file);
      try {
        Files.createDirectory(file);
        fail();
      }
      catch (FileAlreadyExistsException e) {
        // expected
      }

      final Path dir6 = dir1.resolve("file/dir6");
      try {
        Files.createDirectories(dir6);
        fail();
      }
      catch (IOException e) {
        // expected
      }

      final Path dir7 = dir1.resolve("dir7/dir8");
      try {
        Files.createDirectory(dir7);
        fail();
      }
      catch (IOException e) {
        // expected
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testCreateHiddenDirectory () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      assertTrue(Files.isDirectory(base));

      final Path dir1 = base.resolve(".dot");
      Files.createDirectory(dir1);
      assertTrue(Files.isDirectory(dir1));

      final Path file1 = dir1.resolve("file");
      Files.createFile(file1);
      assertTrue(Files.isRegularFile(file1));

      final Path dir2 = dir1.resolve(".dot");
      Files.createDirectory(dir2);
      assertTrue(Files.isDirectory(dir2));

      final Path file2 = dir2.resolve("file");
      Files.createFile(file2);
      assertTrue(Files.isRegularFile(file2));
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testCreateDirectories () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      final Path baz = base.resolve("foo/bar/baz");
      Files.createDirectories(baz);
      assertTrue(Files.exists(baz));
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testSubdirectories () throws Throwable {
    final Path a = selected.resolve("a");
    Files.createDirectory(a);
    try {
      final Path b = a.resolve("b");
      Files.createDirectory(b);

      final Path c = b.resolve("c");
      Files.createDirectory(c);

      final Path d = c.resolve("d");
      Files.createDirectory(d);

      final Path e = d.resolve("e");
      Files.createDirectory(e);

      Files.createFile(e.resolve("f1"));
      Files.createFile(e.resolve("f2"));
      Files.createFile(e.resolve("f3"));
      Files.createFile(e.resolve("f4"));
    }
    finally {
      Files.walkFileTree(a, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testOpenOptions () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      assertTrue(Files.exists(base));

      final Path test = base.resolve("test");
      Files.newOutputStream(test).close();
      assertTrue(Files.exists(test));

      try {
        Files.newOutputStream(test, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).close();
        fail();
      }
      catch (FileAlreadyExistsException e) {
        // expected
      }

      try (final OutputStream out = Files.newOutputStream(test, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        out.write("foo".getBytes(Utils.UTF_8));
      }
      assertEquals(3, Files.size(test));

      Files.delete(test);
      assertFalse(Files.exists(test));
      assertTrue(Files.notExists(test));

      try {
        Files.newByteChannel(test, StandardOpenOption.READ).close();
        fail();
      }
      catch (NoSuchFileException e) {
        // expected
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  /**
   * Will fail on system/mounts which aren't using sdcardfs.
   * BUG: https://code.google.com/p/android/issues/detail?id=18624
   */
  public void testSetLastModifiedTime () throws Throwable {
    final Path file = selected.resolve(getName());
    Files.newOutputStream(file).close();
    try {
      final FileTime time = FileTime.fromMillis(0);
      Files.setLastModifiedTime(file, time);
      assertEquals(time, Files.getLastModifiedTime(file));
    }
    finally {
      Files.delete(file);
    }
  }

  public void testDirectoryStream () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);

      final Path dir1 = base.resolve("dir1");
      Files.createDirectory(dir1);
      final Path dir2 = base.resolve("dir2");
      Files.createDirectory(dir2);
      final Path file1 = base.resolve("file1.foo");
      Files.createFile(file1);
      final Path file2 = base.resolve("file2.bar");
      Files.createFile(file2);

      try (final DirectoryStream<Path> ds = Files.newDirectoryStream(base)) {
        final List<Path> files = Utils.listOf(ds);
        Collections.sort(files);
        assertEquals(4, files.size());
        assertTrue(Files.isSameFile(dir1, files.get(0)));
        assertTrue(Files.isSameFile(dir2, files.get(1)));
        assertTrue(Files.isSameFile(file1, files.get(2)));
        assertTrue(Files.isSameFile(file2, files.get(3)));
      }

      try (final DirectoryStream<Path> ds = Files.newDirectoryStream(base, "*.{foo,bar}")) {
        final List<Path> files = Utils.listOf(ds);
        Collections.sort(files);
        assertEquals(2, files.size());
        assertTrue(Files.isSameFile(file1, files.get(0)));
        assertTrue(Files.isSameFile(file2, files.get(1)));
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testRenameDirectory () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);

      final Path source = base.resolve("source");
      Files.createDirectory(source);
      assertTrue(Files.isDirectory(source));
      final Path foo = Files.createFile(source.resolve("foo"));
      assertTrue(Files.exists(foo));
      final Path bar = Files.createFile(source.resolve("bar"));
      assertTrue(Files.exists(bar));

      final Path target = base.resolve("target");
      Files.move(source, target);
      assertTrue(Files.notExists(source));
      assertTrue(Files.isDirectory(target));

      Files.createFile(source);
      assertTrue(Files.isRegularFile(source));
      try {
        Files.move(target, source);
        fail();
      }
      catch (FileAlreadyExistsException e) {
        // expected
      }

      try {
        Files.delete(target);
        fail();
      }
      catch (DirectoryNotEmptyException e) {
        // expected
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testRenameFile () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);

      final Path source = base.resolve("source");
      Files.newOutputStream(source).close();
      assertTrue(Files.isRegularFile(source));

      final Path target = base.resolve("target");
      Files.move(source, target);
      assertTrue(Files.notExists(source));
      assertTrue(Files.isRegularFile(target));
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  /**
   * Will fail since DocumentProvider doesn't support it.
   */
  public void testPathWithSpace () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);

      final Path file1 = base.resolve(" file1");
      Files.createFile(file1);
      assertTrue(Files.isRegularFile(file1));

      final Path file2 = base.resolve("file2 ");
      Files.createFile(file2);
      assertTrue(Files.isRegularFile(file2));

      final Path file3 = base.resolve(" file3 ");
      Files.createFile(file3);
      assertTrue(Files.isRegularFile(file3));
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  public void testStream () throws Throwable {
    final Path file = selected.resolve(getName());
    try {
      final byte[] data = "foo".getBytes(Utils.UTF_8);
      final byte[] buff = new byte[data.length];
      try (final OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
        out.write(data);
      }
      assertEquals(3, Files.size(file));

      try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
        out.write(data);
      }
      assertEquals(6, Files.size(file));

      try (final InputStream in = Files.newInputStream(file)) {
        assertEquals(buff.length, in.read(buff));
        assertTrue(Arrays.equals(buff, data));
        assertEquals(buff.length, in.read(buff));
        assertTrue(Arrays.equals(buff, data));
      }

      try (final OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        out.write(data);
      }
      assertEquals(3, Files.size(file));
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  public void testByteChannel () throws Throwable {
    final Path file = selected.resolve(getName());
    try {
      try (final SeekableByteChannel sbc = Files.newByteChannel(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        final byte[] data = "foo".getBytes(Utils.UTF_8);
        sbc.write(ByteBuffer.wrap(data));
        assertEquals(sbc.position(), data.length);

        final ByteBuffer bb = ByteBuffer.allocate(data.length);
        assertEquals(sbc.position(0).read(bb), data.length);

        bb.flip();
        final byte[] b = new byte[bb.remaining()];
        bb.get(b);
        assertTrue(Arrays.equals(b, data));
      }
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  public void testParcelFileDescriptor () throws Throwable {
    final Path file = selected.resolve(getName());
    try {
      try (final ParcelFileDescriptor pfd = AndroidFiles.newParcelFileDescriptor(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        assertEquals(0, pfd.getStatSize());
      }
    }
    finally {
      Files.deleteIfExists(file);
    }
  }


  public void testCopy () throws Throwable {
    final Path base = AndroidFiles.getFilesDirectory();
    assertTrue(Files.exists(base));
    final Path source = base.resolve(getName());
    final Path target = selected.resolve(source.getFileName());
    try {
      Files.createFile(source);
      Files.copy(source, target);
      assertTrue(Files.exists(source));
      assertTrue(Files.exists(target));
    }
    finally {
      Files.deleteIfExists(source);
      Files.deleteIfExists(target);
    }
  }

  public void testMove () throws Throwable {
    final Path base = AndroidFiles.getFilesDirectory();
    assertTrue(Files.exists(base));
    final Path source = base.resolve(getName());
    final Path target = selected.resolve(source.getFileName());
    try {
      Files.createFile(source);
      Files.move(source, target);
      assertTrue(Files.notExists(source));
      assertTrue(Files.exists(target));
    }
    finally {
      Files.deleteIfExists(source);
      Files.deleteIfExists(target);
    }
  }

  public void testWatchEventKinds () throws Throwable {
    final WatchEvent.Kind<?>[] kinds = AndroidWatchEventKinds.of(FileObserver.CREATE | FileObserver.DELETE_SELF | 0x1000 | 0x2000); // 0x1000 ignored
    assertNotNull(kinds);
    assertEquals(3, kinds.length);
    assertEquals(AndroidWatchEventKinds.CREATE, kinds[0]);
    assertEquals(AndroidWatchEventKinds.DELETE_SELF, kinds[1]);
    assertEquals(AndroidWatchEventKinds.UNMOUNT, kinds[2]); // 0x2000
  }

  /**
   * Doesn't seem to work for secondary external storage.
   */
  public void testWatchService2 () throws Throwable {
    final Path base = selected.resolve(getName());
    try {
      Files.createDirectory(base);
      try (final WatchService service = fs.newWatchService()) {
        base.register(service, AndroidWatchEventKinds.CREATE);
        base.register(service, AndroidWatchEventKinds.DELETE);
        final Path foo = base.resolve("foo");
        Files.createFile(foo);
        Files.delete(foo);
        final Path bar = base.resolve("bar");
        Files.createFile(bar);
        Files.delete(bar);

        final HashMap<WatchEvent.Kind<?>, Integer> expected = new HashMap<>();
        expected.put(AndroidWatchEventKinds.CREATE, 2);
        expected.put(AndroidWatchEventKinds.DELETE, 2);
        do {
          final WatchKey key = service.poll(2, TimeUnit.SECONDS);
          assertNotNull("timeout", key);
          for (final WatchEvent<?> event : key.pollEvents()) {
            Log.i(getName(), event.toString());
            final Integer remaining = expected.get(event.kind());
            assertNotNull(remaining);
            if (remaining > event.count())
              expected.put(event.kind(), remaining - event.count());
            else
              expected.remove(event.kind());
          }
          key.reset();
        } while (!expected.isEmpty());
      }
    }
    finally {
      Files.walkFileTree(base, Utils.DELETE_FILE_VISITOR);
    }
  }

  @Suppress
  public void testFileStore () throws Throwable {
    // TODO: finish AndroidFileStore impl
    for (final AndroidFileStore store : fs.getAndroidFileStores()) {
      Log.i(getName(), store.toString());
      Log.i(getName(), "  state="+store.state());
      Log.i(getName(), "  read-only="+store.isReadOnly());
      Log.i(getName(), "  removable="+store.isRemovable());
      Log.i(getName(), "  emulated="+store.isEmulated());
      Log.i(getName(), "  total-space="+store.getTotalSpace());
      Log.i(getName(), "  usable-space="+store.getUsableSpace());
      Log.i(getName(), "  unallocated-space="+store.getUnallocatedSpace());
    }
  }

}
