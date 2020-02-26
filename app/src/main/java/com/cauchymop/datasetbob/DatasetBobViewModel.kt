package com.cauchymop.datasetbob

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import com.cauchymop.datasetbob.utils.DriveServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

private const val REQUEST_CODE_SIGN_IN = 1121
private const val REQUEST_CODE_OPEN_DOCUMENT = 2532
private const val TAG = "DatasetBobViewModel"

class DatasetBobViewModel : ViewModel() {

    private var driveServiceHelper: DriveServiceHelper? = null

    fun requestSignIn(activity: Activity) {
        Log.d("Pizza", "Requesting sign-in")
        val signInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE))
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
                    activity, setOf(DriveScopes.DRIVE)
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
                driveServiceHelper = DriveServiceHelper(googleDriveService).also {
                    val queryFiles = it.queryFiles()
                    queryFiles.addOnSuccessListener { println("Files success: ${it.files}") }
//                    it.queryFiles().addOnCompleteListener() { println("Files complete: ${it}") }
                    queryFiles.addOnFailureListener() { println("Files failure: ${it}") }
                    queryFiles.addOnCanceledListener() { println("Files canceled: ${it}") }
                }

            }
            .addOnFailureListener { exception: Exception? ->
                Log.e(TAG, "Unable to sign in.", exception)
            }
    }
}
