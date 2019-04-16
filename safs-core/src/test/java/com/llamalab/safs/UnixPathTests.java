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

package com.llamalab.safs;

import com.llamalab.safs.internal.Utils;

import junit.framework.TestCase;

import java.util.Arrays;

public class UnixPathTests extends TestCase {

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  public void testNameIterable () throws Throwable {
    assertEquals(Arrays.asList(Paths.get("")), Utils.listOf(Paths.get("")));
    assertEquals(Arrays.asList(Paths.get("foo")), Utils.listOf(Paths.get("foo")));
    assertEquals(Arrays.asList(Paths.get("foo"), Paths.get("bar")), Utils.listOf(Paths.get("foo/bar")));
    assertEquals(Arrays.asList(Paths.get("foo")), Utils.listOf(Paths.get("/foo")));
    assertEquals(Arrays.asList(Paths.get("foo"), Paths.get("bar")), Utils.listOf(Paths.get("/foo/bar")));
    assertEquals(Arrays.asList(Paths.get("Test"), Paths.get(".."), Paths.get("Test")), Utils.listOf(Paths.get("/Test/../Test")));
  }

  public void testNameCount () throws Throwable {
    assertEquals(1, Paths.get("").getNameCount());
    assertEquals(0, Paths.get("/").getNameCount());
    assertEquals(1, Paths.get("/foo").getNameCount());
    assertEquals(2, Paths.get("/foo/bar").getNameCount());
    assertEquals(1, Paths.get("foo").getNameCount());
    assertEquals(2, Paths.get("../bar").getNameCount());
    assertEquals(3, Paths.get("/Test/../Test").getNameCount());
  }

  public void testSubpath () throws Throwable {
    try {
      Paths.get("/").subpath(0, 1);
      fail();
    }
    catch (IllegalArgumentException e) {
      // expected
    }
    assertEquals("", Paths.get("").subpath(0, 1).toString());
    assertEquals("foo", Paths.get("foo").subpath(0, 1).toString());
    assertEquals("bar", Paths.get("foo/bar").subpath(1, 2).toString());
    assertEquals("foo", Paths.get("/foo").subpath(0, 1).toString());
    assertEquals("foo/bar", Paths.get("/foo/bar").subpath(0, 2).toString());
    assertEquals("bar", Paths.get("/foo/bar").subpath(1, 2).toString());
  }

  public void testGetName () throws Throwable {
    try {
      Paths.get("/").getName(0);
      fail();
    }
    catch (IllegalArgumentException e) {
      // expected
    }
    assertEquals("", Paths.get("").getName(0).toString());
    assertEquals("foo", Paths.get("foo").getName(0).toString());
    assertEquals("foo", Paths.get("/foo").getName(0).toString());
    assertEquals("bar", Paths.get("foo/bar").getName(1).toString());
    assertEquals("bar", Paths.get("/foo/bar").getName(1).toString());
    try {
      Paths.get("bar").getName(1);
      fail();
    }
    catch (IllegalArgumentException e) {
      // expected
    }
    assertEquals("Test", Paths.get("/Test/../Test").getName(0).toString());
    assertEquals("..", Paths.get("/Test/../Test").getName(1).toString());
  }

  public void testFileName () throws Throwable {
    assertNull(Paths.get("/").getFileName());
    assertEquals("", Paths.get("").getFileName().toString());
    assertEquals("foo", Paths.get("/foo").getFileName().toString());
    assertEquals("bar", Paths.get("/foo/bar").getFileName().toString());
    assertEquals("foo", Paths.get("foo").getFileName().toString());
    assertEquals("bar", Paths.get("foo/bar").getFileName().toString());
  }

  public void testStartsWith () throws Throwable {
    assertTrue(Paths.get("").startsWith(""));
    assertTrue(Paths.get("/foo").startsWith("/"));
    assertTrue(Paths.get("foo").startsWith("foo"));
    assertTrue(Paths.get("foo/bar").startsWith("foo"));
    assertTrue(Paths.get("foo/bar").startsWith("foo/bar"));
    assertTrue(Paths.get("/foo/bar").startsWith("/foo/bar"));
    assertFalse(Paths.get("/foo").startsWith("foo"));
    assertFalse(Paths.get("/foo/bar").startsWith("foo/bar"));
    assertFalse(Paths.get("foo/bar").startsWith("/foo"));
    assertFalse(Paths.get("foo/bar").startsWith("fo"));
    assertFalse(Paths.get("foo").startsWith("f"));
    assertFalse(Paths.get("foo/bar").startsWith(""));
  }

  public void testEndsWith () throws Throwable {
    assertTrue(Paths.get("").endsWith(""));
    assertTrue(Paths.get("/").endsWith("/"));
    assertTrue(Paths.get("foo/..").endsWith(".."));
    assertTrue(Paths.get(".").endsWith("."));
    assertTrue(Paths.get("foo").endsWith("foo"));
    assertTrue(Paths.get("foo/bar").endsWith("bar"));
    assertTrue(Paths.get("foo/bar").endsWith("foo/bar"));
    assertTrue(Paths.get("/foo/bar").endsWith("/foo/bar"));
    assertFalse(Paths.get("foo..").endsWith(".."));
    assertFalse(Paths.get("/foo/bar").endsWith("/foo"));
    assertFalse(Paths.get("/foo").endsWith(""));
    assertFalse(Paths.get("foo/bar").endsWith("ar"));
    assertFalse(Paths.get("bar").endsWith("b"));
    assertFalse(Paths.get("foo/bar").endsWith("/bar"));
    assertFalse(Paths.get("/foo/bar").endsWith("/bar"));
    assertFalse(Paths.get("/foo/bar").endsWith("/bar"));
  }

  public void testParent () throws Throwable {
    assertNull(Paths.get("").getParent());
    assertNull(Paths.get("/").getParent());
    assertEquals("foo/bar", Paths.get("foo/bar/baz").getParent().toString());
    assertEquals("foo", Paths.get("foo/bar").getParent().toString());
    assertNull(Paths.get("foo").getParent());
    assertEquals("/foo/bar", Paths.get("/foo/bar/baz").getParent().toString());
    assertEquals("/foo", Paths.get("/foo/bar").getParent().toString());
    assertEquals("/", Paths.get("/foo").getParent().toString());
  }

  public void testSanitize () throws Throwable {
    assertEquals("",          Paths.get("", "").toString());
    assertEquals("/",         Paths.get("/", "").toString());
    assertEquals("/",         Paths.get("", "/").toString());
    assertEquals("/",         Paths.get("/", "/").toString());
    assertEquals("/",         Paths.get("//////").toString());
    assertEquals("/foo",      Paths.get("///foo///").toString());
    assertEquals("/./foo/.",  Paths.get("//./foo//./").toString());
    assertEquals("foo",       Paths.get("foo/").toString());
    assertEquals("/foo/bar",  Paths.get("/", "foo/", "bar/").toString());
    assertEquals("/foo/bar",  Paths.get("/foo/", "/bar").toString());
    assertEquals("foo/bar",   Paths.get("foo", "bar").toString());
    assertEquals("/",         Paths.get("///").toString());
    assertEquals("/foo/bar",  Paths.get("//foo///bar//").toString());
    assertEquals("foo/bar",   Paths.get("foo/bar///").toString());
    assertEquals("foo/bar",      Paths.get("foo////bar").toString());
    assertEquals("/foo/bar/baz", Paths.get("/foo", "bar////baz").toString());
    assertEquals("/foo/bar",     Paths.get("/foo/","bar////////").toString());
    assertEquals("foo/bar/baz",  Paths.get("foo/bar////////baz").toString());
    assertEquals("a/b/c",        Paths.get("a/b", "c").toString());
    assertEquals("a/b/c",        Paths.get("a", "b", "c").toString());
    assertEquals("a/b/c",        Paths.get("a", "b/c").toString());
    assertEquals("/c",           Paths.get("/", "////c").toString());
  }

  public void testResolve () throws Throwable {
    assertEquals("",      Paths.get("").resolve("").toString());
    assertEquals("/",     Paths.get("").resolve("/").toString());
    assertEquals("/",     Paths.get("/").resolve("").toString());
    assertEquals("/",     Paths.get("/").resolve("/").toString());
    assertEquals("foo",   Paths.get("").resolve("foo").toString());
    assertEquals("/foo",  Paths.get("").resolve("/foo").toString());
  }

  public void testNormalize () throws Throwable {
    assertEquals("/",    Paths.get("/").normalize().toString());
    assertEquals("a/b",  Paths.get("a/./b").normalize().toString());
    assertEquals("a",    Paths.get("a/b/..").normalize().toString());
    assertEquals("",     Paths.get("a/..").normalize().toString());
    assertEquals("..",   Paths.get("..").normalize().toString());
    assertEquals("../a", Paths.get("../a/b/..").normalize().toString());
    assertEquals("",     Paths.get(".").normalize().toString());
  }

  public void testRelativize () throws Throwable {
    assertEquals("c/d",     Paths.get("/a/b").relativize(        Paths.get("/a/b/c/d")).toString());
    assertEquals("../x",    Paths.get("/a/b").relativize(        Paths.get("/a/x")).toString());
    assertEquals("b/c/d",   Paths.get("/a/b/c/../..").relativize(Paths.get("/a/b/c/d")).toString());
    assertEquals("../../x", Paths.get("/a/b/c").relativize(      Paths.get("/a/x")).toString());
    assertEquals("..",      Paths.get("/a/b").relativize(        Paths.get("/a")).toString());
    assertEquals("b/c",     Paths.get("/a/b/..").relativize(     Paths.get("/a/b/c")).toString());
    assertEquals("b",       Paths.get("../a").relativize(        Paths.get("../a/b")).toString());
    assertEquals("c",       Paths.get("a/../b").relativize(Paths.get("a/../b/c")).toString());
    assertEquals("",        Paths.get("a/../b").relativize(Paths.get("a/../b/c/..")).toString());
    assertEquals("..",      Paths.get("a/../b").relativize(Paths.get("a/../b/..")).toString());
    assertEquals("",        Paths.get("./.").relativize(Paths.get(".")).toString());
    assertEquals("../..",   Paths.get("a").relativize(Paths.get("a/../..")).toString());
    assertEquals("",        Paths.get("a").relativize(Paths.get("a/b/..")).toString());
    assertEquals("",        Paths.get("/").relativize(Paths.get("/.")).toString());
  }

  /*
  public void testGetAncestor () throws IOException {
    assertNull(((UnixPath)Paths.get("/")).getAncestor(0));
    assertEquals("/", ((UnixPath)Paths.get("/a/b")).getAncestor(0).toString());
    assertEquals("/a", ((UnixPath)Paths.get("/a/b")).getAncestor(1).toString());
    assertEquals("/a/b", ((UnixPath)Paths.get("/a/b")).getAncestor(2).toString());
    try {
      ((UnixPath)Paths.get("/a/b")).getAncestor(3);
      fail();
    }
    catch (IllegalArgumentException e) {
      // expected
    }
    assertNull(((UnixPath)Paths.get("a/b")).getAncestor(0));
    assertNull(((UnixPath)Paths.get("a")).getAncestor(0));
    assertEquals("a", ((UnixPath)Paths.get("a/b")).getAncestor(1).toString());
    assertEquals("a/b", ((UnixPath)Paths.get("a/b")).getAncestor(2).toString());
    try {
      ((UnixPath)Paths.get("a/b")).getAncestor(3);
      fail();
    }
    catch (IllegalArgumentException e) {
      // expected
    }
  }
  */
}
