package com.cauchymop.datasetbob

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.cauchymop.datasetbob.utils.DriveServiceHelper
import com.cauchymop.datasetbob.utils.SingleLiveEvent
import com.cauchymop.datasetbob.utils.getOutputDirectory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission

private const val REQUEST_CODE_SIGN_IN = 1121
private const val REQUEST_CODE_OPEN_DOCUMENT = 2532
private const val TAG = "DatasetBobViewModel"

private const val DRIVE_SCOPE = DriveScopes.DRIVE_FILE
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val ROOT_FOLDER = "Datasets"
private const val DATASET_SAMPLE_FOLDER = "Sample"

class DatasetBobViewModel(private val rootDirectory: java.io.File) : ViewModel() {

  private lateinit var driveServiceHelper: DriveServiceHelper

  private val EXTENSION_WHITELIST = arrayOf("JPG")

  private val _datasets = MutableLiveData<List<File>>()
  val datasets: LiveData<List<File>> = _datasets

  private val _currentDataset = MutableLiveData<File>()
  val currentDataset: LiveData<File> = _currentDataset

  private val _images = MutableLiveData<List<java.io.File>>()
  val images: LiveData<List<java.io.File>> = _images

  private val categoryFolders = MutableLiveData<List<File>>()
  val categories: LiveData<List<File>> = categoryFolders

  private val _uploadError = SingleLiveEvent<String>()
  val uploadError: LiveData<String> = _uploadError

  private val _uploadProgress = SingleLiveEvent<Boolean>()
  val uploadProgress: LiveData<Boolean> = _uploadProgress

  private val _currentImage: MutableLiveData<java.io.File> = MutableLiveData<java.io.File>()
  val currentImage: LiveData<java.io.File> = _currentImage

  init {
    refreshMediaList()
  }

  fun setCurrentImage(img: java.io.File?) {
    Log.e(TAG, "Setting currentImage to $img")
    _currentImage.postValue(img)
  }

  fun refreshMediaList() {
    // Get root directory of media from navigation arguments

    // Walk through all files in the root directory
    // We reverse the order of the list to present the last photos first
    val fileList: List<java.io.File> = rootDirectory.listFiles { file ->
      EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
    }?.sortedDescending()?.toMutableList() ?: mutableListOf()
    _images.value = fileList
  }

  fun requestSignIn(activity: Activity) {
    Log.d(TAG, "Requesting sign-in")
    val signInOptions =
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_METADATA))
        .build()
    val client = GoogleSignIn.getClient(activity as Activity, signInOptions)
    // The result of the sign-in Intent is handled in onActivityResult.
    activity.startActivityForResult(client.signInIntent, REQUEST_CODE_SIGN_IN)
  }

  /**
   * Handles the `result` of a completed sign-in activity initiated from [ ][.requestSignIn].
   */
  fun handleSignInResult(activity: Activity, result: Intent?) {
    GoogleSignIn.getSignedInAccountFromIntent(result)
      .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
        Log.d(TAG, "Signed in as " + googleAccount.email)
        // Use the authenticated account to sign in to the Drive service.
        val credential = GoogleAccountCredential.usingOAuth2(
          activity, setOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA)
        )
        credential.selectedAccount = googleAccount.account
        val googleDriveService = Drive.Builder(
          AndroidHttp.newCompatibleTransport(),
          GsonFactory(),
          credential
        )
          .setApplicationName("DatasetBob")
          .build()
        // The DriveServiceHelper encapsulates all REST API and SAF functionality.
        // Its instantiation is required before handling any onClick actions.
        driveServiceHelper = DriveServiceHelper(googleDriveService).also { helper ->
          helper.queryFiles(ROOT_FOLDER)
            .addOnSuccessListener { fileList ->
              val rootInstances = fileList.files
              println("Root instances: $rootInstances")
              if (rootInstances.isEmpty()) {
                println("No root instance found. Creating sample hierarchy.")
                driveServiceHelper.createFile(ROOT_FOLDER, mimeType = FOLDER_MIME_TYPE)
                  .continueWithTask {
                    val rootId = it.result!!
                    driveServiceHelper.createFile(
                      DATASET_SAMPLE_FOLDER,
                      parents = listOf(rootId),
                      mimeType = FOLDER_MIME_TYPE
                    )
                  }
                  .continueWithTask { sampleFolderIdTask ->
                    val sampleFolderId = sampleFolderIdTask.result!!
                    with(driveServiceHelper) {
                      addPermission(sampleFolderId, "jerome.abela@gmail.com")
                      addPermission(sampleFolderId, "olivier.bonal@gmail.com")
                      createFile(
                        "label1",
                        parents = listOf(sampleFolderId),
                        mimeType = FOLDER_MIME_TYPE
                      );
                      createFile(
                        "label2",
                        parents = listOf(sampleFolderId),
                        mimeType = FOLDER_MIME_TYPE
                      );
                    }
                  }
                  .addOnSuccessListener {
                    Log.e(TAG, "Sample creation successful $it")
                  }.addOnFailureListener { exception: Exception? ->
                    Log.e(TAG, "Unable to create sample.", exception)
                  }

              } else {
                val root = rootInstances[0]
                helper.queryFolderFiles(root.id).addOnSuccessListener {
                  _datasets.value = it.files
                  println("Read success: $it")
                }
              }
            }
            .addOnFailureListener { println("Files failure: ${it}") }
            .addOnCanceledListener { println("Files canceled") }
        }

      }
      .addOnFailureListener { exception: Exception? ->
        Log.e(TAG, "Unable to sign in.", exception)
      }
  }

  fun selectDataset(selected: File) {
    _currentDataset.value = selected
    driveServiceHelper.queryFolderFiles(selected.id).addOnSuccessListener {
      categoryFolders.value = it.files
      println("Categories: $it")
    }
  }

  fun classify(categoryFolder: File) {
    _uploadProgress.value = true
    val mediaFile = currentImage.value!!
    Log.e(TAG, "Creating ${mediaFile.name}")
    driveServiceHelper.createFile(
      fileName = mediaFile.name,
      parents = listOf(categoryFolder.id)
    ).continueWithTask {
      Log.e(TAG, "Uploading $mediaFile")
      driveServiceHelper.saveFile(
        it.result!!,
        mediaFile.name,
        mediaFile.readBytes(),
        "image/jpeg"
      )
    }.addOnSuccessListener {
      Log.e(TAG, "Upload successful, deleting $mediaFile")
      deleteCurrentImage()
      _uploadProgress.value = false
    }.addOnFailureListener { exception: Exception? ->
      Log.e(TAG, "Unable to save file via REST.", exception)
      _uploadError.value = "Unable to save file" // TODO: Stringify
      _uploadProgress.value = false
    }
  }

  fun deleteCurrentImage() {
    currentImage.value?.let {
      it.delete()
      Log.e(TAG, "Deleted $it")
      refreshMediaList()
    }
  }
}

fun createViewModel(activity: FragmentActivity) =
  ViewModelProviders.of(activity, DatasetBobViewModelFactory(getOutputDirectory(activity)))
    .get(DatasetBobViewModel::class.java)
