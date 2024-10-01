package com.geecee.escape

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit

@Composable
fun WidgetsScreen(
    context: Context,
    modifier: Modifier
) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost = remember { AppWidgetHost(context, 1) }
    var appWidgetId by remember { mutableIntStateOf(getSavedWidgetId(context)) }
    var appWidgetHostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
    var needsConfiguration by remember { mutableStateOf(false) }

    val widgetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            appWidgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                ?: return@rememberLauncherForActivityResult
            saveWidgetId(context, appWidgetId)
            val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            needsConfiguration = isWidgetConfigurable(context, appWidgetId)

            if (needsConfiguration) {
                launchWidgetConfiguration(context, appWidgetId)
            } else {
                appWidgetHostView =
                    appWidgetHost.createView(context, appWidgetId, widgetInfo).apply {
                        setAppWidget(appWidgetId, widgetInfo)
                    }
            }
        }
    }

    LaunchedEffect(appWidgetId) {
        try {
            if (appWidgetId != -1) {
                val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (widgetInfo != null) {

                    appWidgetHostView =
                        appWidgetHost.createView(context, appWidgetId, widgetInfo).apply {
                            setAppWidget(appWidgetId, widgetInfo)
                        }

                }

            } else {
                appWidgetId = appWidgetHost.allocateAppWidgetId()
                val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                widgetPickerLauncher.launch(pickIntent)
            }
        } catch (e: Exception) {
            Log.e("Widget error", e.message.toString())
        }
    }


    appWidgetHostView?.let { hostView ->
        AndroidView(
            factory = { hostView },
            modifier = modifier
        )
    }
}

fun isWidgetConfigurable(context: Context, appWidgetId: Int): Boolean {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return false
    val configureComponent = appWidgetInfo.configure

    // Check if the configuration activity is exported
    val resolveInfo = context.packageManager.resolveActivity(
        Intent().setComponent(configureComponent),
        PackageManager.MATCH_DEFAULT_ONLY
    )
    return resolveInfo != null && resolveInfo.activityInfo.exported
}

fun launchWidgetConfiguration(context: Context, appWidgetId: Int) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
    val configureComponent = appWidgetInfo?.configure

    configureComponent?.let {
        val resolveInfo = context.packageManager.resolveActivity(
            Intent().setComponent(configureComponent),
            PackageManager.MATCH_DEFAULT_ONLY
        )

        if (resolveInfo?.activityInfo?.exported == true) {
            val configureIntent = Intent().apply {
                component = it
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startActivity(configureIntent)
        } else {
            Log.w("WidgetsScreen", "Configuration activity is not exported and cannot be started.")
        }
    }
}

fun saveWidgetId(context: Context, widgetId: Int) {
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putInt("widget_id", widgetId)
    }
}

fun getSavedWidgetId(context: Context): Int {
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    return prefs.getInt("widget_id", -1)
}

fun removeWidget(context: Context) {
    // Save the updated widget ID to shared preferences
    saveWidgetId(context, -1)
}