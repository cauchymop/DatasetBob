/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cauchymop.datasetbob.utils

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.util.Pair
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.Permission
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
class DriveServiceHelper(private val mDriveService: Drive) {
  private val mExecutor: Executor =
    Executors.newSingleThreadExecutor()

  /**
   * Creates a text file in the user's My Drive folder and returns its file ID.
   */
  fun createFile(
    fileName: String,
    parents: List<String> = listOf("root"),
    mimeType: String = "text/plain"
  ): Task<String> {
    return Tasks.call(mExecutor, Callable {
      val metadata =
        File()
          .setParents(parents)
          .setMimeType(mimeType)
          .setName(fileName)
      val googleFile =
        mDriveService.files().create(metadata).execute()
          ?: throw IOException("Null result when requesting file creation.")
      googleFile.id
    })
  }

  /**
   * Opens the file identified by `fileId` and returns a [Pair] of its name and
   * contents.
   */
  fun readFile(fileId: String?): Task<Pair<String, String>> {
    return Tasks.call(
      mExecutor,
      Callable {
        // Retrieve the metadata as a File object.
        val metadata =
          mDriveService.files()[fileId].execute()
        val name = metadata.name
        mDriveService.files()[fileId].executeMediaAsInputStream().use { `is` ->
          BufferedReader(InputStreamReader(`is`)).use { reader ->
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
              stringBuilder.append(line)
            }
            val contents = stringBuilder.toString()
            return@Callable Pair.create(
              name,
              contents
            )
          }
        }
      }
    )
  }

  /**
   * Updates the file identified by `fileId` with the given `name` and `content`.
   */
  fun saveFile(
    fileId: String,
    name: String,
    content: ByteArray,
    mimeType: String
  ): Task<Unit> {
    return Tasks.call(
      mExecutor,
      Callable {

        // Create a File containing any metadata changes.
        val metadata =
          File().setName(name)

        // Convert content to an AbstractInputStreamContent instance.
        val contentStream =
          ByteArrayContent(mimeType, content)

        // Update the metadata and contents.
        mDriveService.files().update(fileId, metadata, contentStream).execute()
        Unit
      }
    )
  }

  /**
   * Returns a [FileList] containing all the visible files in the user's My Drive.
   *
   *
   * The returned list will only contain files visible to this app, i.e. those which were
   * created by this app. To perform operations on files not created by the app, the project must
   * request Drive Full Scope in the [Google
 * Developer's Console](https://play.google.com/apps/publish) and be submitted to Google for verification.
   */
  fun queryFiles(withName: String? = null): Task<FileList> {
    return Tasks.call(
      mExecutor,
      Callable {
        val list = mDriveService.files().list()
        withName?.let { list.setQ("name=\"$it\"") }
        list.setSpaces("drive").execute()
      }
    )
  }

  fun queryFolderFiles(folder: String): Task<FileList> {
    return Tasks.call(
      mExecutor,
      Callable {
        mDriveService.files().list().setQ("\"$folder\" in parents")
          .setSpaces("drive").execute()
      }
    )
  }

  /**
   * Returns an [Intent] for opening the Storage Access Framework file picker.
   */
  fun createFilePickerIntent(): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "text/plain"
    return intent
  }

  /**
   * Opens the file at the `uri` returned by a Storage Access Framework [Intent]
   * created by [.createFilePickerIntent] using the given `contentResolver`.
   */
  fun openFileUsingStorageAccessFramework(
    contentResolver: ContentResolver, uri: Uri?
  ): Task<Pair<String?, String?>> {
    return Tasks.call(
      mExecutor,
      Callable {

        // Retrieve the document's display name from its metadata.
        var name: String? = null
        contentResolver.query(uri!!, null, null, null, null).use { cursor ->
          name = if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.getString(nameIndex)
          } else {
            throw IOException("Empty cursor returned for file.")
          }
        }

        // Read the document's contents as a String.
        var content: String? = null
        contentResolver.openInputStream(uri).use { inputStream ->
          BufferedReader(InputStreamReader(inputStream!!)).use { reader ->
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
              stringBuilder.append(line)
            }
            content = stringBuilder.toString()
          }
        }
        Pair.create(name, content)
      }
    )
  }

  fun addPermission(fileId: String, emailAddress: String) =
      Tasks.call(
          mExecutor,
          Callable {
              mDriveService.permissions().create(
                  fileId, Permission()
                      .setType("user")
                      .setRole("writer")
                      .setEmailAddress(emailAddress)
              ).execute()
          }
      )

}