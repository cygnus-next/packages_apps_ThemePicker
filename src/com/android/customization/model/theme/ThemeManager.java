/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.customization.model.theme;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_ANDROID;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_LAUNCHER;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SETTINGS;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_ICON_SYSUI;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.SETTINGS_PACKAGE;
import static com.android.customization.model.ResourceConstants.SYSUI_PACKAGE;

import android.graphics.Point;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.custom.CustomTheme;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.picker.SetWallpaperDialogFragment.Listener;
import com.android.wallpaper.util.WallpaperCropUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ThemeManager implements CustomizationManager<ThemeBundle> {

    private static final Set<String> THEME_CATEGORIES = new HashSet<>();
    static {
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_COLOR);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_FONT);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_SHAPE);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_ANDROID);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_SETTINGS);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_SYSUI);
        THEME_CATEGORIES.add(OVERLAY_CATEGORY_ICON_LAUNCHER);
    };


    private final ThemeBundleProvider mProvider;
    private final OverlayManagerCompat mOverlayManagerCompat;

    private final WallpaperSetter mWallpaperSetter;
    private final FragmentActivity mActivity;
    private final ThemesUserEventLogger mEventLogger;

    private Map<String, String> mCurrentOverlays;

    public ThemeManager(ThemeBundleProvider provider, FragmentActivity activity,
            WallpaperSetter wallpaperSetter, OverlayManagerCompat overlayManagerCompat,
            ThemesUserEventLogger logger) {
        mProvider = provider;
        mActivity = activity;
        mOverlayManagerCompat = overlayManagerCompat;
        mWallpaperSetter = wallpaperSetter;
        mEventLogger = logger;
    }

    @Override
    public boolean isAvailable() {
        return mProvider.isAvailable();
    }

    @Override
    public void apply(ThemeBundle theme, Callback callback) {
        // Set wallpaper
        if (theme.shouldUseThemeWallpaper()) {
            mWallpaperSetter.requestDestination(mActivity, mActivity.getSupportFragmentManager(),
                    R.string.set_theme_wallpaper_dialog_message, new Listener() {
                        @Override
                        public void onSetHomeScreen() {
                            applyWallpaper(theme, WallpaperPersister.DEST_HOME_SCREEN,
                                    createSetWallpaperCallback(theme, callback));
                        }

                        @Override
                        public void onSetLockScreen() {
                            applyWallpaper(theme, WallpaperPersister.DEST_LOCK_SCREEN,
                                    createSetWallpaperCallback(theme, callback));
                        }

                        @Override
                        public void onSetBoth() {
                            applyWallpaper(theme, WallpaperPersister.DEST_BOTH,
                                    createSetWallpaperCallback(theme, callback));
                        }
                    });

        } else {
            applyOverlays(theme, callback);
        }
    }

    private SetWallpaperCallback createSetWallpaperCallback(ThemeBundle theme, Callback callback) {
        return new SetWallpaperCallback() {
            @Override
            public void onSuccess() {
                applyOverlays(theme, callback);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                callback.onError(throwable);
            }
        };
    }

    private void applyWallpaper(ThemeBundle theme, int destination,
            SetWallpaperCallback callback) {
        Point defaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                mActivity.getResources(),
                mActivity.getWindowManager().getDefaultDisplay());
        Asset wallpaperAsset = theme.getWallpaperInfo().getAsset(mActivity);
        wallpaperAsset.decodeRawDimensions(mActivity,
                dimensions -> {
                    float scale = 1f;
                    // Calculate scale to fit the screen height
                    if (dimensions != null && dimensions.y > 0) {
                        scale = (float) defaultCropSurfaceSize.y / dimensions.y;
                    }
                    mWallpaperSetter.setCurrentWallpaper(mActivity,
                            theme.getWallpaperInfo(),
                            wallpaperAsset,
                            destination,
                            scale, null, callback);
                });
    }

    private void applyOverlays(ThemeBundle theme, Callback callback) {
        boolean allApplied = true;
        if (theme.isDefault()) {
            allApplied &= disableCurrentOverlay(ANDROID_PACKAGE, OVERLAY_CATEGORY_SHAPE);
            allApplied &= disableCurrentOverlay(ANDROID_PACKAGE, OVERLAY_CATEGORY_COLOR);
            allApplied &= disableCurrentOverlay(ANDROID_PACKAGE, OVERLAY_CATEGORY_FONT);
            allApplied &= disableCurrentOverlay(ANDROID_PACKAGE, OVERLAY_CATEGORY_ICON_ANDROID);
            allApplied &= disableCurrentOverlay(SYSUI_PACKAGE, OVERLAY_CATEGORY_ICON_SYSUI);
            allApplied &= disableCurrentOverlay(SETTINGS_PACKAGE, OVERLAY_CATEGORY_ICON_SETTINGS);
            allApplied &= disableCurrentOverlay(ResourceConstants.getLauncherPackage(mActivity),
                    OVERLAY_CATEGORY_ICON_LAUNCHER);
        } else {
            allApplied &= applyOverlayOrDefault(theme, ANDROID_PACKAGE, OVERLAY_CATEGORY_SHAPE);
            allApplied &= applyOverlayOrDefault(theme, ANDROID_PACKAGE, OVERLAY_CATEGORY_COLOR);
            allApplied &= applyOverlayOrDefault(theme, ANDROID_PACKAGE, OVERLAY_CATEGORY_FONT);
            allApplied &= applyOverlayOrDefault(theme, ANDROID_PACKAGE,
                    OVERLAY_CATEGORY_ICON_ANDROID);
            allApplied &= applyOverlayOrDefault(theme, SYSUI_PACKAGE, OVERLAY_CATEGORY_ICON_SYSUI);
            allApplied &= applyOverlayOrDefault(theme, SETTINGS_PACKAGE,
                    OVERLAY_CATEGORY_ICON_SETTINGS);
            allApplied &= applyOverlayOrDefault(theme,
                    ResourceConstants.getLauncherPackage(mActivity),
                    OVERLAY_CATEGORY_ICON_LAUNCHER);
        }
        allApplied &= Settings.Secure.putString(mActivity.getContentResolver(),
                ResourceConstants.THEME_SETTING, theme.getSerializedPackages());
        if (theme instanceof CustomTheme) {
            storeCustomTheme((CustomTheme) theme);
        }
        mCurrentOverlays = null;
        if (allApplied) {
            mEventLogger.logThemeApplied(theme, theme instanceof CustomTheme);
            callback.onSuccess();
        } else {
            callback.onError(null);
        }
    }

    private void storeCustomTheme(CustomTheme theme) {
        mProvider.storeCustomTheme(theme);
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<ThemeBundle> callback, boolean reload) {
        mProvider.fetch(callback, reload);
    }

    private boolean disableCurrentOverlay(String targetPackage, String category) {
        return true;
    }

    private boolean applyOverlayOrDefault(ThemeBundle theme, String targetPkg, String category) {
        return true;
    }

    public Map<String, String> getCurrentOverlays() {
        if (mCurrentOverlays == null) {
            mCurrentOverlays = mOverlayManagerCompat.getEnabledOverlaysForTargets(
                    ResourceConstants.getPackagesToOverlay(mActivity));
            mCurrentOverlays.entrySet().removeIf(
                    categoryAndPackage -> !THEME_CATEGORIES.contains(categoryAndPackage.getKey()));
        }
        return mCurrentOverlays;
    }

    public String getStoredOverlays() {
        return Settings.Secure.getString(mActivity.getContentResolver(),
                ResourceConstants.THEME_SETTING);
    }

    public void removeCustomTheme(CustomTheme theme) {
        mProvider.removeCustomTheme(theme);
    }

    /**
     * @return an existing ThemeBundle that matches the same packages as the given one, if one
     * exists, or {@code null} otherwise.
     */
    @Nullable
    public ThemeBundle findThemeByPackages(ThemeBundle other) {
        return mProvider.findEquivalent(other);
    }
}
