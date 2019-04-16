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

import android.os.Environment;

import com.llamalab.safs.Path;

final class PrimaryFileStore extends AndroidFileStore {

  private final Path path;

  public PrimaryFileStore (Path path) {
    this.path = path;
  }

  @Override
  public String name () {
    return PRIMARY_NAME;
  }

  @Override
  public Path path () {
    return path;
  }

  @Override
  public boolean isPrimary () {
    return true;
  }

  @Override
  public boolean isEmulated () {
    return Environment.isExternalStorageEmulated();
  }

  @Override
  public boolean isRemovable () {
    return Environment.isExternalStorageRemovable();
  }

  @Override
  public String state () {
    return Environment.getExternalStorageState();
  }

}
