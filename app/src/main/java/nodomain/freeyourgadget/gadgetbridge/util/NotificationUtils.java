/*  Copyright (C) 2017-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, José Rebelo, Lukas Veneziano, Maxim Baz

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;

public class NotificationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationUtils.class);

    @NonNull
    public static String getPreferredTextFor(NotificationSpec notificationSpec, int lengthBody, int lengthSubject, Context context) {
        switch (notificationSpec.getType()) {
            case GENERIC_ALARM_CLOCK:
                return StringUtils.getFirstOf(notificationSpec.getTitle(), notificationSpec.getSubject());
            case GENERIC_SMS:
            case GENERIC_EMAIL:
                return formatText(notificationSpec.getSender(), notificationSpec.getSubject(), notificationSpec.getBody(), lengthBody, lengthSubject, context);
            case GENERIC_NAVIGATION:
                return StringUtils.getFirstOf(notificationSpec.getTitle(), notificationSpec.getBody());
            case CONVERSATIONS:
            case FACEBOOK_MESSENGER:
            case GOOGLE_MESSENGER:
            case GOOGLE_HANGOUTS:
            case HIPCHAT:
            case KAKAO_TALK:
            case LINE:
            case SIGNAL:
            case WIRE:
            case SKYPE:
            case SNAPCHAT:
            case TELEGRAM:
            case THREEMA:
            case KONTALK:
            case ANTOX:
            case TWITTER:
            case WHATSAPP:
            case VIBER:
            case WECHAT:
                return StringUtils.ensureNotNull(notificationSpec.getBody());
        }
        return "";
    }

    @NonNull
    public static String formatSender(String sender, Context context) {
        if (sender == null || sender.length() == 0) {
            return "";
        }
        return context.getString(R.string.StringUtils_sender, sender);
    }


    @NonNull
    public static String formatText(String sender, String subject, String body, int lengthBody, int lengthSubject, Context context) {
        String fBody = StringUtils.truncate(body, lengthBody);
        String fSubject = StringUtils.truncate(subject, lengthSubject);
        String fSender = formatSender(sender, context);

        StringBuilder builder = StringUtils.join(" ", fBody, fSubject, fSender);
        return builder.toString().trim();
    }

    public static String getPreferredTextFor(CallSpec callSpec) {
        return StringUtils.getFirstOf(callSpec.getName(), callSpec.getNumber());
    }

    @Nullable
    public static Drawable getAppIcon(final Context context, final String packageName) {
        final int navigationIcon = NavigationUtils.getIconResource(packageName);
        if (navigationIcon != 0) {
            return ResourcesCompat.getDrawable(context.getResources(), navigationIcon, context.getTheme());
        }

        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (final PackageManager.NameNotFoundException ignored) {
            LOG.warn("Failed to find icon for {}, attempting fallback", packageName);
            final LauncherActivityInfo launcherActivityInfo = getLauncherActivityInfo(context, packageName);
            if (launcherActivityInfo != null) {
                return launcherActivityInfo.getIcon(0);
            }

            return null;
        }
    }

    /**
     * Get the icon for a notification: the notification's own small icon if it can be loaded,
     * otherwise the icon of the source app. Apps that are only installed in another profile
     * (e.g. the work profile) are looked up in that profile.
     */
    @Nullable
    public static Drawable getNotificationIcon(final Context context, final NotificationSpec notificationSpec) {
        if (notificationSpec.getIconId() != 0 && notificationSpec.getIconPackageId() != null) {
            final Drawable icon = getPackageDrawable(context, notificationSpec.getIconPackageId(), notificationSpec.getIconId());
            if (icon != null) {
                return icon;
            }
        }

        if (notificationSpec.getSourceAppId() == null) {
            return null;
        }

        return getAppIcon(context, notificationSpec.getSourceAppId());
    }

    /**
     * Load a drawable from another package's resources. If the package is not installed in the
     * current profile, look for it in the other profiles of this user (e.g. the work profile).
     */
    @Nullable
    private static Drawable getPackageDrawable(final Context context, final String packageName, final int resourceId) {
        try {
            final Context packageContext = context.createPackageContext(packageName, 0);
            return ResourcesCompat.getDrawable(packageContext.getResources(), resourceId, null);
        } catch (final PackageManager.NameNotFoundException e) {
            // Not installed in the current profile - try the other profiles below
        } catch (final Exception e) {
            LOG.debug("Failed to load drawable {} from {}: {}", resourceId, packageName, e.getMessage());
            return null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        try {
            final LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            final UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            final UserHandle currentUser = Process.myUserHandle();
            for (final UserHandle userHandle : userManager.getUserProfiles()) {
                if (userHandle.equals(currentUser)) {
                    continue;
                }
                try {
                    final ApplicationInfo applicationInfo = launcherApps.getApplicationInfo(packageName, 0, userHandle);
                    if (applicationInfo == null) {
                        continue;
                    }
                    final Resources resources = context.getPackageManager().getResourcesForApplication(applicationInfo);
                    final Drawable drawable = ResourcesCompat.getDrawable(resources, resourceId, null);
                    if (drawable != null) {
                        LOG.debug("Loaded drawable {} from {} in profile {}", resourceId, packageName, userHandle);
                        return drawable;
                    }
                } catch (final Exception e) {
                    LOG.debug("Failed to load drawable {} from {} in profile {}: {}", resourceId, packageName, userHandle, e.getMessage());
                }
            }
        } catch (final Exception e) {
            LOG.warn("Error while searching other profiles for {}", packageName, e);
        }

        return null;
    }

    @Nullable
    public static String getApplicationLabel(final Context context, final String packageName) {
        final PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (final PackageManager.NameNotFoundException ignored) {
            LOG.warn("Failed to find application label for {}, attempting fallback", packageName);
            final LauncherActivityInfo launcherActivityInfo = getLauncherActivityInfo(context, packageName);
            if (launcherActivityInfo != null) {
                return launcherActivityInfo.getLabel().toString();
            }

            return null;
        }
    }

    /**
     * Fallback method to get an app info - iterate through all the users and attempt to find the
     * app. This includes work profiles.
     */
    @Nullable
    private static LauncherActivityInfo getLauncherActivityInfo(final Context context, final String packageName) {
        try {
            final LauncherApps launcherAppsService = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            final UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            final List<UserHandle> userProfiles = userManager.getUserProfiles();
            // Skip user 0 (current, main user)
            for (int i = 1; i < userProfiles.size(); i++) {
                final UserHandle userHandle = userProfiles.get(i);
                final List<LauncherActivityInfo> activityList = launcherAppsService.getActivityList(packageName, userHandle);

                if (!activityList.isEmpty()) {
                    LOG.debug("Found {} launcher activity infos for {} in user {}", activityList.size(), packageName, userHandle);
                    return activityList.get(0);
                }
            }

            LOG.warn("Failed to find launcher activity info for {}", packageName);
        } catch (final Exception e) {
            LOG.error("Error during launcher activity info search", e);
        }

        return null;
    }

    /**
     * Returns all applications on the device, including applications in work profiles.
     */
    @NonNull
    public static List<String> getAllApplications(final Context context) {
        final PackageManager pm = context.getPackageManager();
        final List<String> ret = new LinkedList<>();

        // Get apps for the current user
        final List<ApplicationInfo> currentUserApps = pm.getInstalledApplications(0);
        for (final ApplicationInfo app : currentUserApps) {
            ret.add(app.packageName);
        }

        // Add all apps from other users (eg. manager profile)
        for (final LauncherActivityInfo app : getOtherProfileActivities(context)) {
            final String packageName = app.getApplicationInfo().packageName;
            if (!ret.contains(packageName)) {
                ret.add(packageName);
            }
        }

        return ret;
    }

    /**
     * Returns the launcher activities of the other user profiles, for example a work profile.
     */
    @NonNull
    public static List<LauncherActivityInfo> getOtherProfileActivities(final Context context) {
        final List<LauncherActivityInfo> ret = new LinkedList<>();
        try {
            final UserHandle currentUser = Process.myUserHandle();
            final LauncherApps launcher = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            for (final UserHandle userProfile : um.getUserProfiles()) {
                if (!userProfile.equals(currentUser)) {
                    ret.addAll(launcher.getActivityList(null, userProfile));
                }
            }
        } catch (final Exception e) {
            LOG.error("Failed to get apps from other users", e);
        }
        return ret;
    }

}
