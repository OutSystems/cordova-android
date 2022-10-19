/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.apache.cordova;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressLint("LongLogTag")
public class SplashScreenPlugin extends CordovaPlugin {
    static final String PLUGIN_NAME = "CordovaSplashScreenPlugin";

    private static final boolean DEFAULT_HAS_CUSTOM_SPLASHSCREENS = false;

    // Default config preference values
    private static final boolean DEFAULT_AUTO_HIDE = true;
    private static final int DEFAULT_DELAY_TIME = -1; // milliseconds
    private static final boolean DEFAULT_FADE = true;
    private static final int DEFAULT_FADE_TIME = 500; // milliseconds

    // Default legacy config preference values
    private static final String DEFAULT_SPLASH_RESOURCE = "screen";
    private static final boolean DEFAULT_SHOW_ONLY_FIRST_TIME = true;
    private static final boolean DEFAULT_MAINTAIN_ASPECT_RATIO = false;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    private static final boolean DEFAULT_SHOW_SPINNER = true;
    private static final String DEFAULT_SPINNER_COLOR = null;

    // Config preference values
    /**
     * Boolean flag to auto hide splash screen (default=true)
     */
    private boolean autoHide;
    /**
     * Integer value of how long to delay in milliseconds (default=-1)
     */
    private int delayTime;
    /**
     * Boolean flag if to fade to fade out splash screen (default=true)
     */
    private boolean isFadeEnabled;
    /**
     * Integer value of the fade duration in milliseconds (default=500)
     */
    private int fadeDuration;

    // Internal variables
    /**
     * Boolean flag to determine if the splash screen remains visible.
     */
    private boolean keepOnScreen = true;

    private SplashScreenBehaviourCollection behaviours;

    @Override
    protected void pluginInitialize() {
        behaviours = new SplashScreenBehaviourCollection();

        // Auto Hide & Delay Settings
        boolean autoHide = preferences.getBoolean("AutoHideSplashScreen", DEFAULT_AUTO_HIDE);
        int delayTime = preferences.getInteger("SplashScreenDelay", DEFAULT_DELAY_TIME);
        LOG.d(PLUGIN_NAME, "Auto Hide: " + autoHide);
        if (delayTime != DEFAULT_DELAY_TIME) {
            LOG.d(PLUGIN_NAME, "Delay: " + delayTime + "ms");
        }

        // Fade & Fade Duration
        boolean isFadeEnabled = preferences.getBoolean("FadeSplashScreen", DEFAULT_FADE);
        int fadeDuration = preferences.getInteger("FadeSplashScreenDuration", DEFAULT_FADE_TIME);
        LOG.d(PLUGIN_NAME, "Fade: " + isFadeEnabled);
        if (isFadeEnabled) {
            LOG.d(PLUGIN_NAME, "Fade Duration: " + fadeDuration + "ms");
        }

        Context context = cordova.getContext();
        boolean showSpinner = preferences.getBoolean("ShowSplashScreenSpinner", DEFAULT_SHOW_SPINNER);
        boolean hasCustomSplashscreens = preferences.getBoolean("HasCustomSplashscreens", DEFAULT_HAS_CUSTOM_SPLASHSCREENS);
        if (!showSpinner && !hasCustomSplashscreens) {
            // Use only the Android Splashscreen API
            behaviours.registerBehaviour(new AndroidSplashScreenBehaviour(context, autoHide, delayTime, isFadeEnabled, fadeDuration));

        } else {
            // Using the Android Splashscreen API is mandatory, so we'll try to use it as least as possible
            behaviours.registerBehaviour(new AndroidSplashScreenBehaviour(context, true, 1, false, 0));

            // And we'll use the legacy code, from cordova-plugin-splashscreen
            Activity activity = cordova.getActivity();
            String splashResource = preferences.getString("SplashScreen", DEFAULT_SPLASH_RESOURCE);
            boolean showOnlyFirstTime = preferences.getBoolean("SplashShowOnlyFirstTime", DEFAULT_SHOW_ONLY_FIRST_TIME);
            boolean maintainAspectRatio = preferences.getBoolean("SplashMaintainAspectRatio", DEFAULT_MAINTAIN_ASPECT_RATIO);
            int backgroundColor = preferences.getInteger("backgroundColor", DEFAULT_BACKGROUND_COLOR);
            String spinnerColor = preferences.getString("SplashScreenSpinnerColor", DEFAULT_SPINNER_COLOR);

            behaviours.registerBehaviour(new LegacySplashScreenBehaviour(
                    autoHide,
                    delayTime,
                    isFadeEnabled,
                    fadeDuration,
                    splashResource,
                    showOnlyFirstTime,
                    maintainAspectRatio,
                    backgroundColor,
                    showSpinner,
                    spinnerColor,
                    activity,
                    webView.getView(),
                    webView
            ));
        }
    }

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext
    ) throws JSONException {
        if (action.equals("show")) {
            behaviours.runInAllBehaviours(SplashScreenBehaviour::show);
        } else if (action.equals("hide")) {
            behaviours.runInAllBehaviours(SplashScreenBehaviour::hide);
        } else {
            return false;
        }

        callbackContext.success();
        return true;
    }

    @Override
    public Object onMessage(String id, Object data) {
        behaviours.runInAllBehaviours(behaviour -> behaviour.onMessage(id, data));
        return null;
    }

    @Override
    public void onPause(boolean multitasking) {
        behaviours.runInAllBehaviours(SplashScreenBehaviour::onPauseOrDestroy);
    }

    @Override
    public void onDestroy() {
        behaviours.runInAllBehaviours(SplashScreenBehaviour::onPauseOrDestroy);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        behaviours.runInAllBehaviours(behaviour -> behaviour.onConfigurationChanged(newConfig));
    }

    private interface SplashScreenBehaviour {
        void show();
        void hide();
        void onPauseOrDestroy();
        void onConfigurationChanged(Configuration newConfig);
        void onMessage(String id, Object data);
    }

    private static class SplashScreenBehaviourCollection {
        private List<SplashScreenBehaviour> behaviours;

        public SplashScreenBehaviourCollection() {
            this.behaviours = new ArrayList<>();
        }

        public synchronized void registerBehaviour(SplashScreenBehaviour behaviour) {
            behaviours.add(behaviour);
        }

        public synchronized void runInAllBehaviours(Consumer<SplashScreenBehaviour> lambda) {
            for (SplashScreenBehaviour behaviour : behaviours) {
                lambda.accept(behaviour);
            }
        }
    }

    private static class AndroidSplashScreenBehaviour implements SplashScreenBehaviour {
        // Context
        private Context context;

        // Configuration variables
        private boolean autoHide;
        private int delayTime;
        private boolean isFadeEnabled;
        private int fadeDuration;

        // Internal variables
        private boolean keepOnScreen;

        public AndroidSplashScreenBehaviour(Context context, boolean autoHide, int delayTime, boolean isFadeEnabled, int fadeDuration) {
            // Context
            this.context = context;
            // Configuration variables
            this.autoHide = autoHide;
            this.delayTime = delayTime;
            this.isFadeEnabled = isFadeEnabled;
            this.fadeDuration = fadeDuration;
            // Internal variables
            this.keepOnScreen = true;
        }

        @Override
        public void show() {
            // no-op
            // The Android splash screen is always shown at startup
            // It can also not be shown again afterwards
            // So this method does nothing
        }

        @Override
        public void hide() {
            keepOnScreen = false;
        }

        @Override
        public void onPauseOrDestroy() {
            // no-op
            // Everything is managed by Android, so there is nothing to cleanup
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            // no-op
            // Everything is managed by Android, so there is nothing to manage
        }

        @Override
        public void onMessage(String id, Object data) {
            switch (id) {
                case "setupSplashScreen":
                    setupSplashScreen((SplashScreen) data);
                    break;

                case "onPageFinished":
                    attemptCloseOnPageFinished();
                    break;
            }
        }

        private void setupSplashScreen(SplashScreen splashScreen) {
            // Setup Splash Screen Delay
            splashScreen.setKeepOnScreenCondition(() -> keepOnScreen);

            // auto hide splash screen when custom delay is defined.
            if (autoHide && delayTime > 0) {
                Handler splashScreenDelayHandler = new Handler(context.getMainLooper());
                splashScreenDelayHandler.postDelayed(() -> keepOnScreen = false, delayTime);
            }

            // auto hide splash screen with default delay (-1) delay is controlled by the
            // `onPageFinished` message.

            // If auto hide is disabled (false), the hiding of the splash screen must be determined &
            // triggered by the front-end code with the `navigator.splashscreen.hide()` method.

            if (isFadeEnabled) {
                // Setup the fade
                splashScreen.setOnExitAnimationListener(new SplashScreen.OnExitAnimationListener() {
                    @Override
                    public void onSplashScreenExit(@NonNull SplashScreenViewProvider splashScreenViewProvider) {
                        View splashScreenView = splashScreenViewProvider.getView();

                        splashScreenView
                                .animate()
                                .alpha(0.0f)
                                .setDuration(fadeDuration)
                                .setStartDelay(0)
                                .setInterpolator(new AccelerateInterpolator())
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        splashScreenViewProvider.remove();
                                    }
                                }).start();
                    }
                });
            }
        }

        private void attemptCloseOnPageFinished() {
            if (autoHide && delayTime <= 0) {
                keepOnScreen = false;
            }
        }
    }

    private static class LegacySplashScreenBehaviour implements SplashScreenBehaviour {
        // Configuration variables
        private boolean autoHide;
        private int delayTime;
        private boolean isFadeEnabled;
        private int fadeDuration;
        private String splashResource;
        private boolean showOnlyFirstTime;
        private boolean maintainAspectRatio;
        private int backgroundColor;
        private boolean showSpinner;
        private String spinnerColor;
        // Activity variables
        private Activity activity;
        private View webView;
        private CordovaWebView cordovaWebView;
        // Internal variables
        private Dialog splashDialog;
        private ProgressDialog spinnerDialog;
        private boolean firstShow;
        private boolean lastHideAfterDelay; // https://issues.apache.org/jira/browse/CB-9094
        private ImageView splashImageView;
        private int orientation;

        public LegacySplashScreenBehaviour(
                boolean autoHide,
                int delayTime,
                boolean isFadeEnabled,
                int fadeDuration,
                String splashResource,
                boolean showOnlyFirstTime,
                boolean maintainAspectRatio,
                int backgroundColor,
                boolean showSpinner,
                String spinnerColor,
                Activity activity,
                View webView,
                CordovaWebView cordovaWebView
        ) {
            // Configuration variables
            this.autoHide = autoHide;
            this.delayTime = delayTime;
            this.isFadeEnabled = isFadeEnabled;
            this.fadeDuration = fadeDuration;
            this.splashResource = splashResource;
            this.showOnlyFirstTime = showOnlyFirstTime;
            this.maintainAspectRatio = maintainAspectRatio;
            this.backgroundColor = backgroundColor;
            this.showSpinner = showSpinner;
            this.spinnerColor = spinnerColor;
            // Activity variables
            this.activity = activity;
            this.webView = webView;
            this.cordovaWebView = cordovaWebView;
            // Internal variables
            this.splashDialog = null;
            this.spinnerDialog = null;
            this.firstShow = true;
            this.lastHideAfterDelay = false;
            this.splashImageView = null;
            this.orientation = 0;

            // Make WebView invisible while loading URL
            // CB-11326 Ensure we're calling this on UI thread
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.setVisibility(View.INVISIBLE);
                }
            });

            // Save initial orientation.
            orientation = activity.getResources().getConfiguration().orientation;

            if (firstShow) {
                showSplashScreen(autoHide);
            }

            if (showOnlyFirstTime) {
                firstShow = false;
            }
        }

        private int getSplashId() {
            int drawableId = 0;
            if (splashResource != null) {
                drawableId = activity.getResources().getIdentifier(splashResource, "drawable", activity.getClass().getPackage().getName());
                if (drawableId == 0) {
                    drawableId = activity.getResources().getIdentifier(splashResource, "drawable", activity.getPackageName());
                }
            }
            return drawableId;
        }

        private int getFadeDuration () {
            int fadeSplashScreenDuration = isFadeEnabled ? fadeDuration : 0;

            if (fadeSplashScreenDuration < 30) {
                // [CB-9750] This value used to be in decimal seconds, so we will assume that if someone specifies 10
                // they mean 10 seconds, and not the meaningless 10ms
                fadeSplashScreenDuration *= 1000;
            }

            return fadeSplashScreenDuration;
        }

        @Override
        public void show() {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    cordovaWebView.postMessage("splashscreen", "show");
                }
            });
        }

        @Override
        public void hide() {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    cordovaWebView.postMessage("splashscreen", "hide");
                }
            });
        }

        @Override
        public void onPauseOrDestroy() {
            // hide the splash screen to avoid leaking a window
            this.removeSplashScreen(true);
        }

        @Override
        public void onMessage(String id, Object data) {
            if ("splashscreen".equals(id)) {
                if ("hide".equals(data.toString())) {
                    this.removeSplashScreen(false);
                } else {
                    this.showSplashScreen(false);
                }
            } else if ("spinner".equals(id)) {
                if ("stop".equals(data.toString())) {
                    webView.setVisibility(View.VISIBLE);
                }
            } else if ("onReceivedError".equals(id)) {
                this.spinnerStop();
            }
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            if (newConfig.orientation != orientation) {
                orientation = newConfig.orientation;

                // Splash drawable may change with orientation, so reload it.
                if (splashImageView != null) {
                    int drawableId = getSplashId();
                    if (drawableId != 0) {
                        splashImageView.setImageDrawable(activity.getResources().getDrawable(drawableId));
                    }
                }
            }
        }

        private void removeSplashScreen(final boolean forceHideImmediately) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    if (splashDialog != null && splashImageView != null && splashDialog.isShowing()) {//check for non-null splashImageView, see https://issues.apache.org/jira/browse/CB-12277
                        final int fadeSplashScreenDuration = getFadeDuration();
                        // CB-10692 If the plugin is being paused/destroyed, skip the fading and hide it immediately
                        if (fadeSplashScreenDuration > 0 && forceHideImmediately == false) {
                            AlphaAnimation fadeOut = new AlphaAnimation(1, 0);
                            fadeOut.setInterpolator(new DecelerateInterpolator());
                            fadeOut.setDuration(fadeSplashScreenDuration);

                            splashImageView.setAnimation(fadeOut);
                            splashImageView.startAnimation(fadeOut);

                            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {
                                    spinnerStop();
                                }

                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    if (splashDialog != null && splashImageView != null && splashDialog.isShowing()) {//check for non-null splashImageView, see https://issues.apache.org/jira/browse/CB-12277
                                        splashDialog.dismiss();
                                        splashDialog = null;
                                        splashImageView = null;
                                    }
                                }

                                @Override
                                public void onAnimationRepeat(Animation animation) {
                                }
                            });
                        } else {
                            spinnerStop();
                            splashDialog.dismiss();
                            splashDialog = null;
                            splashImageView = null;
                        }
                    }
                }
            });
        }

        /**
         * Shows the splash screen over the full Activity
         */
        @SuppressWarnings("deprecation")
        private void showSplashScreen(final boolean hideAfterDelay) {
            final int drawableId = getSplashId();

            final int fadeSplashScreenDuration = getFadeDuration();
            final int effectiveSplashDuration = Math.max(0, delayTime - fadeSplashScreenDuration);

            lastHideAfterDelay = hideAfterDelay;

            // Prevent to show the splash dialog if the activity is in the process of finishing
            if (activity.isFinishing()) {
                return;
            }
            // If the splash dialog is showing don't try to show it again
            if (splashDialog != null && splashDialog.isShowing()) {
                return;
            }
            if (drawableId == 0 || (delayTime <= 0 && hideAfterDelay)) {
                return;
            }

            activity.runOnUiThread(new Runnable() {
                public void run() {
                    // Get reference to display
                    Display display = activity.getWindowManager().getDefaultDisplay();
                    Context context = webView.getContext();

                    // Use an ImageView to render the image because of its flexible scaling options.
                    splashImageView = new ImageView(context);
                    splashImageView.setImageResource(drawableId);
                    LayoutParams layoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                    splashImageView.setLayoutParams(layoutParams);

                    splashImageView.setMinimumHeight(display.getHeight());
                    splashImageView.setMinimumWidth(display.getWidth());

                    // TODO: Use the background color of the webView's parent instead of using the preference.
                    splashImageView.setBackgroundColor(backgroundColor);

                    if (maintainAspectRatio) {
                        // CENTER_CROP scale mode is equivalent to CSS "background-size:cover"
                        splashImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                    else {
                        // FIT_XY scales image non-uniformly to fit into image view.
                        splashImageView.setScaleType(ImageView.ScaleType.FIT_XY);
                    }

                    // Create and show the dialog
                    splashDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
                    // check to see if the splash screen should be full screen
                    if ((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN)
                            == WindowManager.LayoutParams.FLAG_FULLSCREEN) {
                        splashDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    }
                    splashDialog.setContentView(splashImageView);
                    splashDialog.setCancelable(false);
                    splashDialog.show();

                    if (showSpinner) {
                        spinnerStart();
                    }

                    // Set Runnable to remove splash screen just in case
                    if (hideAfterDelay) {
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                if (lastHideAfterDelay) {
                                    removeSplashScreen(false);
                                }
                            }
                        }, effectiveSplashDuration);
                    }
                }
            });
        }

        // Show only spinner in the center of the screen
        private void spinnerStart() {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    spinnerStop();

                    spinnerDialog = new ProgressDialog(webView.getContext());
                    spinnerDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            spinnerDialog = null;
                        }
                    });

                    spinnerDialog.setCancelable(false);
                    spinnerDialog.setIndeterminate(true);

                    RelativeLayout centeredLayout = new RelativeLayout(activity);
                    centeredLayout.setGravity(Gravity.CENTER);
                    centeredLayout.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

                    ProgressBar progressBar = new ProgressBar(webView.getContext());
                    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                    progressBar.setLayoutParams(layoutParams);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        if(spinnerColor != null){
                            int[][] states = new int[][] {
                                    new int[] { android.R.attr.state_enabled}, // enabled
                                    new int[] {-android.R.attr.state_enabled}, // disabled
                                    new int[] {-android.R.attr.state_checked}, // unchecked
                                    new int[] { android.R.attr.state_pressed}  // pressed
                            };
                            int progressBarColor = Color.parseColor(spinnerColor);
                            int[] colors = new int[] {
                                    progressBarColor,
                                    progressBarColor,
                                    progressBarColor,
                                    progressBarColor
                            };
                            ColorStateList colorStateList = new ColorStateList(states, colors);
                            progressBar.setIndeterminateTintList(colorStateList);
                        }
                    }

                    centeredLayout.addView(progressBar);

                    spinnerDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    spinnerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                    spinnerDialog.show();
                    spinnerDialog.setContentView(centeredLayout);
                }
            });
        }

        private void spinnerStop() {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    if (spinnerDialog != null && spinnerDialog.isShowing()) {
                        spinnerDialog.dismiss();
                        spinnerDialog = null;
                    }
                }
            });
        }
    }
}
