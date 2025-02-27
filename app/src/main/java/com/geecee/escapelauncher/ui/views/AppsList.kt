package com.geecee.escapelauncher.ui.views

import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.geecee.escapelauncher.R
import com.geecee.escapelauncher.utils.AppUtils
import com.geecee.escapelauncher.utils.AppUtils.resetHome
import com.geecee.escapelauncher.utils.PrivateAppItem
import com.geecee.escapelauncher.utils.PrivateSpaceSettings
import com.geecee.escapelauncher.utils.getBooleanSetting
import com.geecee.escapelauncher.utils.getPrivateSpaceApps
import com.geecee.escapelauncher.utils.isPrivateSpace
import com.geecee.escapelauncher.utils.lockPrivateSpace
import com.geecee.escapelauncher.utils.managers.getUsageForApp
import com.geecee.escapelauncher.utils.openPrivateSpaceApp
import com.geecee.escapelauncher.utils.unlockPrivateSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.geecee.escapelauncher.MainAppViewModel as MainAppModel

//Apps list from the pager
@Composable
fun AppsList(
    mainAppModel: MainAppModel, homeScreenModel: HomeScreenModel
) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
    ) {
        LazyColumn(
            state = homeScreenModel.appsListScrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp, 0.dp),
            horizontalAlignment = AppUtils.getAppsListAlignmentFromPreferences(
                homeScreenModel.sharedPreferences, mainAppModel.getContext()
            )
        ) {
            item {
                AppsListHeader()
            }

            item {
                if (homeScreenModel.sharedPreferences.getBoolean(
                        stringResource(R.string.ShowSearchBox), true
                    )
                ) {
                    Spacer(modifier = Modifier.height(15.dp))

                    AnimatedPillSearchBar(textChange = { searchBoxText ->
                        homeScreenModel.searchText.value = searchBoxText
                        val autoOpen = homeScreenModel.sharedPreferences.getBoolean(
                            mainAppModel.getContext().resources.getString(R.string.SearchAutoOpen),
                            false
                        )

                        if (autoOpen && AppUtils.filterAndSortApps(
                                homeScreenModel.installedApps,
                                homeScreenModel.searchText.value,
                                mainAppModel.packageManager
                            ).size == 1
                        ) {


                            val appInfo = AppUtils.filterAndSortApps(
                                homeScreenModel.installedApps,
                                homeScreenModel.searchText.value,
                                mainAppModel.packageManager
                            ).first()
                            homeScreenModel.currentPackageName.value =
                                appInfo.activityInfo.packageName

                            AppUtils.openApp(
                                homeScreenModel.currentPackageName.value,
                                false,
                                homeScreenModel.showOpenChallenge,
                                mainAppModel
                            )

                            resetHome(homeScreenModel, mainAppModel)


                        }
                    }, keyboardDone = { searchBoxText ->
                        if (AppUtils.filterAndSortApps(
                                homeScreenModel.installedApps,
                                searchBoxText,
                                mainAppModel.packageManager
                            ).isNotEmpty()
                        ) {
                            val firstAppInfo = AppUtils.filterAndSortApps(
                                homeScreenModel.installedApps,
                                searchBoxText,
                                mainAppModel.packageManager
                            ).first()

                            val packageName = firstAppInfo.activityInfo.packageName
                            homeScreenModel.currentPackageName.value = packageName

                            AppUtils.openApp(
                                homeScreenModel.currentPackageName.value,
                                false,
                                homeScreenModel.showOpenChallenge,
                                mainAppModel
                            )

                            resetHome(homeScreenModel, mainAppModel)
                        }
                    }, expanded = homeScreenModel.searchExpanded
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                }
            }

            items(homeScreenModel.sortedInstalledApps.filter { appInfo ->
                val appName = appInfo.loadLabel(mainAppModel.packageManager).toString()
                if (homeScreenModel.searchExpanded.value) {
                    appName.contains(homeScreenModel.searchText.value, ignoreCase = true)
                } else {
                    true
                }
            })
            { app ->
                // Fetch screen time in a coroutine
                val appScreenTime = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                LaunchedEffect(mainAppModel.shouldReloadAppUsageOnApps.value) {
                    withContext(Dispatchers.IO) {
                        appScreenTime.longValue = getUsageForApp(
                            app.activityInfo.packageName,
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        )
                        mainAppModel.shouldReloadAppUsageOnApps.value = false
                    }
                }

                // Draw app if its not hidden and not Escape itself
                if (!app.activityInfo.packageName.contains("com.geecee.escapelauncher") && !mainAppModel.hiddenAppsManager.isAppHidden(
                        app.activityInfo.packageName
                    )
                ) {
                    HomeScreenItem(
                        appName = AppUtils.getAppNameFromPackageName(
                            mainAppModel.getContext(),
                            app.activityInfo.packageName
                        ),
                        screenTime = appScreenTime.longValue,
                        onAppClick = {
                            val packageName = app.activityInfo.packageName
                            homeScreenModel.currentPackageName.value = packageName

                            AppUtils.openApp(
                                packageName, false, homeScreenModel.showOpenChallenge, mainAppModel
                            )

                            resetHome(homeScreenModel, mainAppModel)
                        },
                        onAppLongClick = {
                            homeScreenModel.showBottomSheet.value = true
                            homeScreenModel.currentSelectedApp.value =
                                AppUtils.getAppNameFromPackageName(
                                    mainAppModel.getContext(), app.activityInfo.packageName
                                )
                            homeScreenModel.currentPackageName.value = app.activityInfo.packageName
                            homeScreenModel.isCurrentAppChallenged.value =
                                mainAppModel.challengesManager.doesAppHaveChallenge(
                                    app.activityInfo.packageName
                                )
                            homeScreenModel.isCurrentAppHidden.value =
                                mainAppModel.hiddenAppsManager.isAppHidden(
                                    app.activityInfo.packageName
                                )
                            homeScreenModel.isCurrentAppFavorite.value =
                                mainAppModel.favoriteAppsManager.isAppFavorite(
                                    app.activityInfo.packageName
                                )

                            if (getBooleanSetting(
                                    mainAppModel.getContext(),
                                    mainAppModel.getContext().resources.getString(R.string.Haptic),
                                    true
                                )
                            ) {
                                homeScreenModel.haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        showScreenTime = getBooleanSetting(
                            mainAppModel.getContext(),
                            stringResource(R.string.ScreenTimeOnApp)
                        ),
                        modifier = Modifier
                    )
                }
            }

            //Private space
            if (AppUtils.isDefaultLauncher(mainAppModel.getContext()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                mainAppModel.showPrivateSpaceUnlockedUI.value =
                    isPrivateSpace(mainAppModel.getContext())
                item {
                    if ((!mainAppModel.showPrivateSpaceUnlockedUI.value && !getBooleanSetting(
                            mainAppModel.getContext(),
                            stringResource(R.string.SearchHiddenPrivateSpace),
                            false
                        )) || (!mainAppModel.showPrivateSpaceUnlockedUI.value && homeScreenModel.searchText.value.contains(
                            stringResource(R.string.private_space_search_term)
                        ) && getBooleanSetting(
                            mainAppModel.getContext(),
                            stringResource(R.string.SearchHiddenPrivateSpace),
                            false
                        ))
                    ) {
                        Button({
                            unlockPrivateSpace(mainAppModel.getContext())
                            mainAppModel.shouldGoHomeOnResume.value = false
                        }) {
                            Text(stringResource(R.string.unlock_private_space))
                        }
                    }

                    AnimatedVisibility(
                        visible = mainAppModel.showPrivateSpaceUnlockedUI.value,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        PrivateSpace(mainAppModel, homeScreenModel)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }

        // Private space settings
        AnimatedVisibility(
            visible = homeScreenModel.showPrivateSpaceSettings.value && mainAppModel.showPrivateSpaceUnlockedUI.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PrivateSpaceSettings(
                mainAppModel.getContext()
            ) {
                homeScreenModel.showPrivateSpaceSettings.value = false
            }
        }
    }
}

@Composable
fun AppsListHeader() {
    Spacer(modifier = Modifier.height(140.dp))
    Text(
        text = stringResource(id = R.string.all_apps),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.titleMedium
    )
}

//Search bar in the apps list
@Composable
fun AnimatedPillSearchBar(
    textChange: (searchText: String) -> Unit,
    keyboardDone: (searchText: String) -> Unit,
    expanded: MutableState<Boolean>
) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }


    // Animate the width of the search bar
    val width by animateDpAsState(targetValue = if (expanded.value) 280.dp else 150.dp, label = "")

    // Animate the alpha of the text field content
    val alpha by animateFloatAsState(targetValue = if (expanded.value) 1f else 0f, label = "")

    // FocusRequester to request focus on the text field
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(expanded.value) {
        if (expanded.value) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        if (!expanded.value) {
            keyboardController?.hide()
        }
    }

    Surface(modifier = Modifier
        .width(width)
        .height(56.dp)
        .clickable {
            expanded.value = !expanded.value
        }
        .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .animateContentSize()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Icon",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .padding(5.dp, 0.dp)
                    .size(25.dp)
            )

            if (!expanded.value) {
                Text(
                    stringResource(id = R.string.search),
                    modifier = Modifier.animateContentSize(),
                    color = MaterialTheme.colorScheme.background,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (expanded.value) {
                Spacer(
                    modifier = Modifier
                        .width(8.dp)
                        .animateContentSize()
                )

                BasicTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        textChange(searchText.text)
                    },
                    modifier = Modifier
                        .alpha(alpha)
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .animateContentSize(),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (alpha > 0) {
                            innerTextField()
                        }
                    },
                    maxLines = 1,
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        keyboardDone(searchText.text)
                    }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    }
}

//Shown when private space is unlocked
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun PrivateSpace(mainAppModel: MainAppModel, homeScreenModel: HomeScreenModel) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge),
        colors = CardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
        ) {

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    stringResource(R.string.private_space),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Row(
                    Modifier.align(Alignment.CenterEnd)
                ) {
                    IconButton(
                        {
                            homeScreenModel.showPrivateSpaceSettings.value = true
                        }, Modifier, colors = IconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Settings, stringResource(R.string.private_space_settings)
                        )
                    }

                    IconButton(
                        {
                            lockPrivateSpace(mainAppModel.getContext())
                            homeScreenModel.searchExpanded.value = false
                            homeScreenModel.searchText.value = ""
                        }, Modifier, colors = IconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Lock, stringResource(R.string.lock_private_space)
                        )
                    }
                }
            }

            getPrivateSpaceApps(mainAppModel.getContext()).forEach { app ->
                PrivateAppItem(app.displayName, {

                }) {
                    openPrivateSpaceApp(
                        privateSpaceApp = app, context = mainAppModel.getContext(), Rect()
                    )
                    resetHome(homeScreenModel, mainAppModel)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}