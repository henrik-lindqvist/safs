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

import com.llamalab.safs.attributes.FileTime;
import com.llamalab.safs.internal.Utils;

import junit.framework.TestCase;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MiscTests extends TestCase {

  public void testFileTime () {
    assertEquals("1970-01-01T00:00:00Z", FileTime.fromMillis(0).toString());
    assertEquals("1970-01-01T00:00:00.001Z", FileTime.fromMillis(1).toString());
  }


  public void testRfc3339 () {
    assertEquals(0L, Utils.parseRfc3339("1970-01-01T00:00:00Z"));
    assertEquals(datetime(1985,4,12, 23,20,50,52,"UTC"), Utils.parseRfc3339("1985-04-12T23:20:50.52Z"));
    assertEquals(datetime(1996,12,19, 16,39,57,0,"-8:00"), Utils.parseRfc3339("1996-12-19T16:39:57-08:00"));
    assertEquals("1970-01-01T00:00:00Z", Utils.formatRfc3339(0));
    assertEquals("1985-04-12T23:20:50.052Z", Utils.formatRfc3339(datetime(1985, 4, 12, 23, 20, 50, 52, "UTC")));
  }

  private static long datetime (int year, int month, int day, int hour, int minute, int second, int millis, String tz) {
    final GregorianCalendar gc = new GregorianCalendar(TimeZone.getTimeZone(tz), Locale.US);
    //noinspection MagicConstant
    gc.set(year, month - 1, day, hour, minute, second);
    gc.set(Calendar.MILLISECOND, millis);
    return gc.getTimeInMillis();
  }


  public void testGlob () {

    assertMatches(   compileGlob("foo"), "foo");
    assertNotMatches(compileGlob("bar"), "foo");
    assertNotMatches(compileGlob("foo"), "f");

    assertMatches(   compileGlob("*"), "");
    assertMatches(   compileGlob("*"), "foo");
    assertMatches(   compileGlob("f*"), "foo");
    assertMatches(   compileGlob("*o"), "foo");
    assertMatches(   compileGlob("f*r"), "foobar");
    assertNotMatches(compileGlob("f*b"), "foobar");
    assertMatches(   compileGlob("f*b*r"), "foobar");
    assertMatches(   compileGlob("*foo*"), "\u200E\u202Afoobar\u202C\u200E"); // RTL

    assertNotMatches(compileGlob("?"), "");
    assertMatches(   compileGlob("???"), "foo");
    assertNotMatches(compileGlob("??"), "foo");
    assertMatches(   compileGlob("f??"), "foo");
    assertNotMatches(compileGlob("b??"), "foo");
    assertNotMatches(compileGlob("f?"), "foo");
    assertMatches(   compileGlob("fo?"), "foo");
    assertMatches(   compileGlob("?oo"), "foo");
    assertNotMatches(compileGlob("???"), "f/o");

    assertMatches(   compileGlob("f*/b*"), "foo/bar");
    assertMatches(   compileGlob("f**"), "foo/bar");
    assertMatches(   compileGlob("foo/**/baz"), "foo/bar/baz");
    assertMatches(   compileGlob("foo/**/bax"), "foo/bar/baz/bax");
    assertNotMatches(compileGlob("foo/*/bax"), "foo/bar/baz/bax");

    assertMatches(   compileGlob("*.java"), "foo.java");
    assertMatches(   compileGlob("*.*"), "foo.java");
    assertMatches(   compileGlob("*.{java,class}"), "foo.java");
    assertMatches(   compileGlob("foo.?"), "foo.x");
    assertMatches(   compileGlob("/home/*/*"), "/home/gus/data");
    assertMatches(   compileGlob("/home/**"), "/home/gus");
    assertMatches(   compileGlob("/home/**"), "/home/gus/data");

    assertNotMatches(compileGlob("*.{xml,png}"), "foo.java");
    assertNotMatches(compileGlob("foo.?"), "foo.");

    assertMatches(   compileGlob("*.{java,class}"), "foo.class");
    assertNotMatches(compileGlob("*.{java,class}"), "foo.bar");

    assertMatches(   compileGlob("[f]oo"), "foo");
    assertNotMatches(compileGlob("[!f]oo"), "foo");
    assertMatches(   compileGlob("[!f]oo"), "boo");
    assertMatches(   compileGlob("[!a-y]oo"), "zoo");
    assertMatches(   compileGlob("[a-y]oo"), "xoo");
    assertMatches(   compileGlob("[-]oo"), "-oo");
    assertNotMatches(compileGlob("[!-]oo"), "-oo");

    assertMatches(   compileGlob("b\\*z"), "b*z");
    assertNotMatches(compileGlob("b\\*z"), "baz");
    assertMatches(   compileGlob("b\\?z"), "b?z");
    assertNotMatches(compileGlob("b\\?z"), "baz");

    assertMatches(   compileGlob("b\\z"), "bz");
    assertMatches(   compileGlob("b\\\\z"), "b\\z");

    try {
      compileGlob("{foo,bar{baz}}");
      fail();
    }
    catch (PatternSyntaxException e) {
      // expected
    }
    try {
      compileGlob("[z-a]");
      fail();
    }
    catch (PatternSyntaxException e) {
      // expected
    }
  }

  private static Pattern compileGlob (String glob) {
    final String regex = Utils.globToRegex(glob, 0, glob.length());
    //System.out.println(regex);
    return Pattern.compile(regex);
  }

  private static void assertMatches (Pattern pattern, CharSequence input) {
    assertTrue(pattern.matcher(input).matches());
  }
  private static void assertNotMatches (Pattern pattern, CharSequence input) {
    assertFalse(pattern.matcher(input).matches());
  }
}
