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

import com.llamalab.safs.attributes.BasicFileAttributes;

import java.io.IOException;

public class SimpleFileVisitor<T> implements FileVisitor<T> {

  @Override
  public FileVisitResult preVisitDirectory (T dir, BasicFileAttributes attrs) throws IOException {
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult postVisitDirectory (T dir, IOException e) throws IOException {
    if (e != null)
      throw e;
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFile (T file, BasicFileAttributes attrs) throws IOException {
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFileFailed (T file, IOException e) throws IOException {
    throw e;
  }

}
