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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

public final class OpenDocumentTreeActivity extends Activity {

  private static final int REQUEST_CODE_REQUEST_PERMISSIONS = 0;
  private static final int REQUEST_CODE_OPEN_DOCUMENT = 1;

  public Intent resultIntent;

  @SuppressLint("NewApi")
  @Override
  protected void onCreate (Bundle state) {
    super.onCreate(state);
    //noinspection ConstantConditions
    if (   Build.VERSION_CODES.M <= BuildConfig.TARGET_SDK_INT
        && Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
      requestPermissions(new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE },
          REQUEST_CODE_REQUEST_PERMISSIONS);
    }
    else if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT)
      openDocumentTree();
  }

  @TargetApi(Build.VERSION_CODES.M)
  @Override
  public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode) {
      case REQUEST_CODE_REQUEST_PERMISSIONS:
        if (grantResults.length == 0)
          finish(); // cancelled
        else {
          for (final int grantResult : grantResults) {
            if (PackageManager.PERMISSION_GRANTED != grantResult) {
              finish();
              return;
            }
          }
          openDocumentTree();
        }
        break;
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  @Override
  protected void onActivityResult (int requestCode, int resultCode, Intent resultIntent) {
    switch (requestCode) {
      case REQUEST_CODE_OPEN_DOCUMENT:
        if (RESULT_OK == resultCode)
          this.resultIntent = resultIntent;
        else
          finish();
        break;
      default:
        super.onActivityResult(requestCode, resultCode, resultIntent);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  private void openDocumentTree () {
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE_OPEN_DOCUMENT);
  }
}
