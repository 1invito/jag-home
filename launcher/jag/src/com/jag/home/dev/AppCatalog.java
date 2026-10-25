package com.jag.home.dev;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.List;

/** Queries only the six favorites. Rasterize bounded artwork on the worker thread. */
final class AppCatalog {
    static final class App {
        final String id, status;
        final Intent intent;
        final Bitmap art;
        App(String id, String status, Intent intent, Bitmap art) {
            this.id = id;
            this.status = status;
            this.intent = intent;
            this.art = art;
        }
    }

    private AppCatalog() {}

    static Intent launch(PackageManager pm, String id) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(id, 0);
            if (!info.enabled) return null;
            Intent intent = pm.getLeanbackLaunchIntentForPackage(id);
            if (intent == null) intent = pm.getLaunchIntentForPackage(id);
            if (intent == null) return null;
            ResolveInfo resolved = pm.resolveActivity(intent, 0);
            return resolved != null && resolved.activityInfo != null && resolved.activityInfo.exported
                && resolved.activityInfo.enabled ? intent : null;
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return null;
        }
    }

    static App load(Context c, String id) {
        PackageManager pm = c.getPackageManager();
        boolean installed;
        try {
            pm.getApplicationInfo(id, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            installed = false;
        }
        Intent intent = launch(pm, id);
        Bitmap art = null;
        if (intent != null) {
            try {
                ResolveInfo resolved = pm.resolveActivity(intent, 0);
                Drawable image = resolved.activityInfo.loadBanner(pm);
                if (image == null) image = pm.getApplicationBanner(id);
                boolean banner = image != null;
                if (image == null) image = pm.getApplicationIcon(id);
                if (image != null) {
                    int width = banner ? 384 : 128, height = banner ? 216 : 128;
                    art = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    int iw = Math.max(1, image.getIntrinsicWidth()), ih = Math.max(1, image.getIntrinsicHeight());
                    float scale = Math.min(width / (float) iw, height / (float) ih);
                    int w = Math.round(iw * scale), h = Math.round(ih * scale);
                    image.setBounds((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
                    image.draw(new Canvas(art));
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException | OutOfMemoryError e) {
                // A launchable app can still be opened when its artwork cannot be loaded.
                art = null;
            }
        }
        return new App(id, Favorites.availability(installed, intent != null), intent, art);
    }

    static void release(List<App> apps) {
        for (App app : apps) if (app.art != null && !app.art.isRecycled()) app.art.recycle();
    }
}
