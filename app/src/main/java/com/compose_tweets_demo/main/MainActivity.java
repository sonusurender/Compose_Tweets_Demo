package com.compose_tweets_demo.main;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.compose_tweets_demo.R;
import com.compose_tweets_demo.utils.CameraUtils;
import com.compose_tweets_demo.utils.FileNameCreation;
import com.compose_tweets_demo.utils.MarshMallowPermission;
import com.squareup.picasso.Picasso;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.tweetcomposer.ComposerActivity;
import com.twitter.sdk.android.tweetcomposer.TweetComposer;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ImageView pickedImageView;

    /**
     * REQUEST CODE for taking permission and picking/capturing image
     */
    private static final int GALLERY_REQUEST_CODE = 332;
    private static final int CAMERA_REQUEST_CODE = 333;
    private static final int SHARE_PERMISSION_CODE = 223;

    //URI of picked/captured image
    private Uri cameraFileURI;

    //Twitter auth client to do custom Twitter login
    private TwitterAuthClient client;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //find id of ImageView
        pickedImageView = findViewById(R.id.picked_image_view);

    }

    /**
     * method called when user click on pickImageView
     *
     * @param view of the calling element
     */
    public void triggerPickImageTask(View view) {
        checkStorageAndCameraPermission();
    }

    /**
     * check if the app has the CAMERA and STORAGE permission to perform the operation
     * This method will automatically ask permission to user if permission is not granted.
     */
    private void checkStorageAndCameraPermission() {
        if (MarshMallowPermission.checkMashMallowPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, SHARE_PERMISSION_CODE)) {
            onPermissionGranted();
        }
    }

    /**
     * method called when user want to share image and text using Twitter Composer
     * NOTE : If the Twitter app is not installed, the intent will launch twitter.com in a browser,
     * but the specified image will be ignored.
     *
     * @param view of the calling element
     */
    public void shareUsingTwitterComposer(View view) {
        //check if user has picked/captured image or not
        if (cameraFileURI != null) {
            TweetComposer.Builder builder = new TweetComposer.Builder(this)
                    .text("This is a testing tweet!!")//pass any tweet message here
                    .image(cameraFileURI);//pass captured/picked image URI
            builder.show();
        } else {
            //if not then show dialog to pick/capture image
            Toast.makeText(this, "Please select image first to share.", Toast.LENGTH_SHORT).show();
            checkStorageAndCameraPermission();
        }
    }

    /**
     * method to share picked/capture image with text using Twitter Native Composer
     * NOTE : For this you should authenticate user before sharing image as the builder required TwitterSession.
     * It does not depend on the Twitter for Android app being installed.
     *
     * @param view
     */
    public void shareUsingTwitterNativeComposer(View view) {
        //check if user has picked/captured image or not
        if (cameraFileURI != null) {
            TwitterSession session = TwitterCore.getInstance().getSessionManager()
                    .getActiveSession();//get the active session
            if (session != null) {
                //if active session is not null start sharing image
                shareUsingNativeComposer(session);
            } else {
                //if there is no active session then ask user to authenticate
                authenticateUser();
            }
        } else {
            //if not then show dialog to pick/capture image
            Toast.makeText(this, "Please select image first to share.", Toast.LENGTH_SHORT).show();
            checkStorageAndCameraPermission();
        }
    }

    /**
     * method to share image using Twitter Native Kit composer
     *
     * @param session of the authenticated user
     */
    private void shareUsingNativeComposer(TwitterSession session) {
        Intent intent = new ComposerActivity.Builder(this)
                .session(session)//Set the TwitterSession of the User to Tweet
                .image(cameraFileURI)//Attach an image to the Tweet
                .text("This is Native Kit Composer Tweet!!")//Text to prefill in composer
                .hashtags("#android")//Hashtags to prefill in composer
                .createIntent();//finally create intent
        startActivity(intent);
    }

    /**
     * method call to authenticate user
     */
    private void authenticateUser() {
        client = new TwitterAuthClient();//init twitter auth client
        client.authorize(this, new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> twitterSessionResult) {
                //if user is successfully authorized start sharing image
                Toast.makeText(MainActivity.this, "Login successful.", Toast.LENGTH_SHORT).show();
                shareUsingNativeComposer(twitterSessionResult.data);
            }

            @Override
            public void failure(TwitterException e) {
                //if user failed to authorize then show toast
                Toast.makeText(MainActivity.this, "Failed to authenticate by Twitter. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * when permission is granted for CAMERA and STORAGE show alert dialog with two options : CAMERA and GALLERY
     */
    private void onPermissionGranted() {
        new AlertDialog.Builder(this)
                .setTitle("Select Option")
                .setItems(new String[]{"Gallery", "Camera"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                //Gallery
                                selectImageFromGallery();
                                break;
                            case 1:
                                //Camera
                                captureImageFormCamera();
                                break;
                        }
                    }
                })
                .setCancelable(true)
                .create()
                .show();
    }

    /**
     * start activity to pick image from gallery
     */
    private void selectImageFromGallery() {
        Intent in = new Intent(Intent.ACTION_PICK);
        in.setType("image/*");
        startActivityForResult(in, GALLERY_REQUEST_CODE);
    }

    /**
     * start activity to capture image
     */
    private void captureImageFormCamera() {
        //check if device support camera or not if not then don't do anything
        if (!CameraUtils.isDeviceSupportCamera(this)) {
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        //Get the file URI using the below code
        //here in place of AUTHORITY you have to pass <package_name>.file_provider
        //NOTE :For more details check this link  :https://developer.android.com/reference/android/support/v4/content/FileProvider.html
        cameraFileURI = FileProvider.getUriForFile(this, "com.compose_tweets_demo.file_provider", FileNameCreation.createImageFile(this));

        //after getting image URI pass it via Intent
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFileURI);

        //grant URI permission to access the create image URI
        for (ResolveInfo resolveInfo : getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            grantUriPermission(resolveInfo.activityInfo.packageName, cameraFileURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        //here check if there ia any app available to perform camera task or not if not then show toast
        //NOTE : This condition is not required because every device has Camera app but in rare cases some device don't have camera
        //so to avoid that thing we have to add this condition
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No apps to capture image.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            //check if all multiple permissions are granted or not
            case SHARE_PERMISSION_CODE:
                if (permissions.length > 0 && grantResults.length > 0) {
                    int counter = 0;
                    for (int result : grantResults) {
                        if (result != 0) {
                            onPermissionDenied();
                            return;
                        }
                        counter++;

                    }
                    if (counter == permissions.length) {
                        //All permission granted
                        onPermissionGranted();
                    }
                }
                break;
        }
    }

    /**
     * if any one of the permission is denied show a dialog to user to grant permission again if they want
     */
    private void onPermissionDenied() {
        new AlertDialog.Builder(this)
                .setMessage("Both permission are required to pick/capture image. Do you want to try again.")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //if user click ok then again ask for permission
                        checkStorageAndCameraPermission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //if user click on cancel show toast
                        Toast.makeText(MainActivity.this, "You cannot share the images without giving these permissions.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GALLERY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    //get the picked image URI
                    Uri imageUri = data.getData();
                    //set the picked image URI to created variable
                    this.cameraFileURI = imageUri;

                    //now display picked image over ImageView
                    displayImage(imageUri);
                } else {

                    //if user cancelled or failed to pick image show toast
                    Toast.makeText(this, "Failed to pick up image from gallery.", Toast.LENGTH_SHORT).show();
                }
                break;
            case CAMERA_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    //check if Camera URI is null or not
                    if (cameraFileURI != null) {
                        //if not null then show URI over image view
                        displayImage(cameraFileURI);
                    } else {
                        //if URI is null show toast
                        Toast.makeText(this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    //if user cancelled capture image then show toast
                    Toast.makeText(this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                //put this here as Twitter requires to send result back to our Class
                if (client != null)
                    client.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    /**
     * method to show URI over image uri
     * NOTE : I am using picasso to load images as picasso will automatically scale large size images
     * and display very efficiently over ImageView
     *
     * @param imageUri of the picked/captured image
     */
    private void displayImage(Uri imageUri) {
        Picasso.with(this).load(imageUri).into(pickedImageView);
    }


}
