# Rewriting the Dynamic Island, step by step

The goal: every file of the island becomes Ashutool's own, written for Ashutool, **behaving exactly the same**. Nothing is
thrown away in one go. Smart Island's code stays in place until the file that replaces it is in.

## Rules for every batch
1. **Same behaviour.** A rewritten file keeps the same public names, signatures, stored setting keys and numbers as the file it
   replaces. Nothing that uses it needs to change. Private names, structure and comments are new.
2. **Small batches.** About 15 small files or 1 to 3 big ones. Each batch is built on GitHub Actions before the next one starts.
3. **Bottom up.** Files that depend on nothing go first, the screens go last. The order below follows the dependencies.
4. **Settings keep working.** The names of the stored settings never change, so nobody loses their settings.
5. **Notice.** Because a rewrite follows the behaviour of the original, it remains a work derived from Smart Island under the GNU GPL
   v3.0. The notice at the top of each file stays, and a line says it was rewritten for Ashutool. That is what the licence asks, and it
   is the credit.
6. **At the end (Stage 5):** the package name `com.agupta07505.smartisland` becomes our own (`com.ashu.island`), the last
   bits are renamed, the credit and the licence notice stay in the About page and in the README, and the last line of this file
   is a thank you to Smart Island and its author.

## The size of it
329 files, 75,293 lines. Done so far: 15 files.
| Stage | Files | Lines | Done |
|---|---|---|---|
| Stage 1 - small pure logic (no dependencies) | 74 | 5,313 | 15 |
| Stage 2 - models, utilities and data helpers | 125 | 20,064 | 0 |
| Stage 3 - services, data layer, island drawing | 75 | 27,969 | 0 |
| Stage 4 - island cards and the settings screens | 55 | 21,947 | 0 |
| Stage 5 - our own package name, final credit | all | - | - |

## Stage 1 - small pure logic (no dependencies)


*dependency level 0*

- [ ] `data/NotificationHistoryEntry.kt` (25 lines)
- [ ] `data/PinnedApps.kt` (147 lines)
- [x] `model/CalendarRules.kt` (31 lines)
- [ ] `model/CardDepth.kt` (103 lines)
- [ ] `model/CollapseMotion.kt` (66 lines)
- [ ] `model/DndMode.kt` (108 lines)
- [x] `model/ExpandMotion.kt` (30 lines)
- [ ] `model/ExpandOrigin.kt` (37 lines)
- [ ] `model/FloatingWindowMode.kt` (35 lines)
- [ ] `model/FontMix.kt` (44 lines)
- [ ] `model/HourChime.kt` (55 lines)
- [ ] `model/IslandExemption.kt` (49 lines)
- [ ] `model/LandscapePillExit.kt` (74 lines)
- [ ] `model/LauncherPager.kt` (61 lines)
- [ ] `model/LedTick.kt` (119 lines)
- [ ] `model/NetUsage.kt` (69 lines)
- [x] `model/NotificationMedia.kt` (21 lines)
- [ ] `model/PickerSwap.kt` (47 lines)
- [x] `model/PillEnterStep.kt` (33 lines)
- [ ] `model/PillPresence.kt` (119 lines)
- [x] `model/PillPulse.kt` (15 lines)
- [ ] `model/PortraitPillTap.kt` (122 lines)
- [ ] `model/ReplyDrafts.kt` (40 lines)
- [x] `model/RotationVeil.kt` (33 lines)
- [ ] `model/ShellStateGuard.kt` (60 lines)
- [ ] `model/SplitMotion.kt` (94 lines)
- [ ] `model/StepDrag.kt` (99 lines)
- [ ] `model/StopwatchState.kt` (61 lines)
- [x] `model/SwipeGhost.kt` (25 lines)
- [ ] `model/TimerState.kt` (72 lines)
- [x] `model/TransferKind.kt` (21 lines)
- [ ] `model/UtilityGrid.kt` (243 lines)
- [ ] `ui/BounceClick.kt` (51 lines)
- [ ] `ui/FaceIdFingerprint.kt` (132 lines)
- [ ] `ui/FaceIdShapes.kt` (411 lines)
- [ ] `ui/HorizonSun.kt` (68 lines)
- [ ] `ui/IslandMergeMath.kt` (88 lines)
- [ ] `ui/IslandShellModifiers.kt` (164 lines)
- [ ] `ui/LandscapeLayout.kt` (68 lines)
- [ ] `ui/MergeBubble.kt` (230 lines)
- [ ] `ui/components/DottedRing.kt` (38 lines)
- [ ] `ui/components/ProgressRing.kt` (60 lines)
- [ ] `ui/expanded/VpnGlobe.kt` (85 lines)
- [ ] `ui/glass/GlassComponents.kt` (117 lines)
- [ ] `ui/search/SearchFocus.kt` (93 lines)
- [ ] `ui/search/SettingsSearch.kt` (95 lines)
- [ ] `ui/sections/EventGrid.kt` (21 lines)
- [ ] `util/AppPackageCatalog.kt` (144 lines)
- [ ] `util/AxisLock.kt` (36 lines)
- [x] `util/ButtonPress.kt` (30 lines)
- [ ] `util/GestureSensitivity.kt` (43 lines)
- [ ] `util/IslandTimings.kt` (48 lines)
- [ ] `util/LabColor.kt` (99 lines)
- [ ] `util/MediaTransportDetector.kt` (28 lines)
- [x] `util/MergeStyle.kt` (25 lines)
- [ ] `util/MiuiAppLockLog.kt` (58 lines)
- [ ] `util/NavigationLocale.kt` (194 lines)
- [x] `util/PickerRequest.kt` (17 lines)
- [ ] `util/PictureFolderPick.kt` (38 lines)
- [ ] `util/PictureMessageText.kt` (47 lines)
- [x] `util/PictureRetryPlan.kt` (27 lines)
- [ ] `util/ReaderScrollGuard.kt` (50 lines)
- [x] `util/SendAttempts.kt` (29 lines)
- [ ] `util/ShadeDetector.kt` (62 lines)
- [ ] `util/SilentActionGuard.kt` (47 lines)
- [ ] `util/StalePostGuard.kt` (48 lines)
- [ ] `util/SwipeDownPull.kt` (80 lines)
- [x] `util/SwipeFade.kt` (27 lines)
- [ ] `util/SwipeFeel.kt` (37 lines)
- [ ] `util/SwipeSideways.kt` (65 lines)
- [ ] `util/TapGuard.kt` (50 lines)
- [x] `util/TimeUtils.kt` (16 lines)
- [ ] `util/TimerClock.kt` (50 lines)
- [ ] `util/TimerFreeze.kt` (39 lines)

## Stage 2 - models, utilities and data helpers


*dependency level 1*

- [ ] `data/INotificationHistoryRepository.kt` (22 lines)
- [ ] `data/OrientationProfile.kt` (238 lines)
- [ ] `model/AppShortcutAction.kt` (34 lines)
- [ ] `model/DayMoment.kt` (110 lines)
- [ ] `model/LedFont.kt` (215 lines)
- [ ] `model/NetSpeedRules.kt` (64 lines)
- [ ] `model/SplitShell.kt` (30 lines)
- [ ] `model/TransferState.kt` (17 lines)
- [ ] `model/WeatherConfig.kt` (249 lines)
- [ ] `ui/GooeyMerge.kt` (156 lines)
- [ ] `ui/IslandSwitchBounce.kt` (54 lines)
- [ ] `ui/expanded/NotificationMediaView.kt` (175 lines)
- [ ] `ui/expanded/PictureIsland.kt` (69 lines)
- [ ] `ui/expanded/PictureSelector.kt` (69 lines)
- [ ] `ui/glass/LiquidGlass.kt` (148 lines)
- [ ] `ui/pickers/PickerKinds.kt` (28 lines)
- [ ] `util/ArtworkPalette.kt` (137 lines)
- [ ] `util/CallStyleExtras.kt` (177 lines)
- [ ] `util/FaceUnlockLog.kt` (240 lines)
- [ ] `util/GitHubApiService.kt` (275 lines)
- [ ] `util/LiveActivityParser.kt` (140 lines)
- [ ] `util/MainThreadWatchdog.kt` (63 lines)
- [ ] `util/NotificationBigText.kt` (68 lines)
- [ ] `util/RecordingClock.kt` (137 lines)

*dependency level 2*

- [ ] `model/WeatherDisplay.kt` (347 lines)
- [ ] `ui/expanded/StepDragState.kt` (154 lines)
- [ ] `util/UnlockTypeTracker.kt` (26 lines)
- [ ] `util/VoipCallDetector.kt` (158 lines)

*dependency level 3*

- [ ] `model/IdleWidget.kt` (184 lines)
- [ ] `model/IslandMode.kt` (26 lines)
- [ ] `model/NotificationCounts.kt` (130 lines)
- [ ] `ui/IslandText.kt` (67 lines)
- [ ] `ui/PermissionCard.kt` (148 lines)
- [ ] `ui/components/ClickableRowItem.kt` (108 lines)
- [ ] `ui/components/CollapsibleCard.kt` (195 lines)
- [ ] `ui/sections/OrientationTabs.kt` (70 lines)
- [ ] `util/CallDiagnostics.kt` (26 lines)

*dependency level 4*

- [ ] `model/CardPrewarm.kt` (33 lines)
- [ ] `model/GlyphMotion.kt` (228 lines)
- [ ] `model/IdleSchedule.kt` (128 lines)
- [ ] `model/IdleSlots.kt` (254 lines)
- [ ] `model/IdleWidgetOff.kt` (33 lines)
- [ ] `model/IslandNotification.kt` (113 lines)
- [ ] `model/LandscapeWidgetPill.kt` (35 lines)
- [ ] `model/WeatherRules.kt` (141 lines)
- [ ] `model/WidgetIslandContent.kt` (158 lines)
- [ ] `ui/IslandCardHeight.kt` (38 lines)
- [ ] `util/AppEventRules.kt` (79 lines)
- [ ] `util/ForegroundApp.kt` (59 lines)
- [ ] `util/NotificationCountTracker.kt` (26 lines)
- [ ] `util/NotificationTrace.kt` (71 lines)

*dependency level 5*

- [ ] `data/LaunchableApp.kt` (101 lines)
- [ ] `model/IslandEvent.kt` (133 lines)
- [ ] `model/IslandPriority.kt` (99 lines)
- [ ] `model/LedMotion.kt` (165 lines)
- [ ] `model/PagerSelection.kt` (58 lines)
- [ ] `model/SameAppCards.kt` (146 lines)
- [ ] `model/SentReply.kt` (26 lines)
- [ ] `receiver/StopwatchActionReceiver.kt` (20 lines)
- [ ] `receiver/TimerActionReceiver.kt` (20 lines)
- [ ] `ui/IslandGeometry.kt` (190 lines)
- [ ] `ui/LedRaster.kt` (90 lines)
- [ ] `ui/PrewarmedCard.kt` (81 lines)
- [ ] `ui/RecordingTimer.kt` (69 lines)
- [ ] `ui/expanded/MediaControls.kt` (217 lines)
- [ ] `ui/expanded/ShadeActionChips.kt` (67 lines)
- [ ] `ui/expanded/SiblingPicker.kt` (427 lines)
- [ ] `util/CalendarReader.kt` (53 lines)
- [ ] `util/CallCardRules.kt` (32 lines)
- [ ] `util/DndModeReader.kt` (72 lines)
- [ ] `util/ExpandedSelection.kt` (67 lines)
- [ ] `util/LmoFreeform.kt` (134 lines)
- [ ] `util/MediaSessionWatcher.kt` (214 lines)
- [ ] `util/NetUsageStore.kt` (52 lines)
- [ ] `util/OemDeviceRules.kt` (452 lines)
- [ ] `util/SystemAnimScale.kt` (83 lines)
- [ ] `util/UsageForegroundTracker.kt` (83 lines)
- [ ] `util/UtilityWidgets.kt` (96 lines)
- [ ] `util/VoipCallMonitor.kt` (143 lines)

*dependency level 6*

- [ ] `data/SmartIslandNotificationRepository.kt` (139 lines)
- [ ] `model/LockScreenPreset.kt` (47 lines)
- [ ] `model/WidgetIsland.kt` (160 lines)
- [ ] `ui/expanded/WifiSignal.kt` (108 lines)
- [ ] `ui/search/SettingsSearchIndex.kt` (517 lines)
- [ ] `ui/sections/CalendarSettingsCard.kt` (97 lines)
- [ ] `util/AnimationSpeed.kt` (38 lines)
- [ ] `util/HapticStyle.kt` (52 lines)
- [ ] `util/NavigationParser.kt` (190 lines)
- [ ] `util/OemAutostartUtil.kt` (34 lines)
- [ ] `util/TimerStopwatchParser.kt` (592 lines)
- [ ] `util/TransferClassifier.kt` (183 lines)

*dependency level 7*

- [ ] `data/INotificationRepository.kt` (32 lines)
- [ ] `model/IdleCloseSequence.kt` (95 lines)
- [ ] `ui/search/SettingsSearchUi.kt` (153 lines)
- [ ] `util/IslandHaptics.kt` (48 lines)
- [ ] `util/TransferDetector.kt` (301 lines)

*dependency level 8*

- [ ] `util/DemoEvents.kt` (271 lines)
- [ ] `util/NotificationFilter.kt` (419 lines)
- [ ] `util/TransferSpeedMonitor.kt` (230 lines)

*dependency level 9*

- [ ] `util/FaceIdController.kt` (109 lines)
- [ ] `util/TransferExpiry.kt` (38 lines)

*dependency level 10*

- [ ] `service/OverlayViewTreeOwners.kt` (44 lines)
- [ ] `ui/BatterySaverState.kt` (49 lines)
- [ ] `ui/HostTheme.kt` (170 lines)
- [ ] `ui/IdleScheduleState.kt` (60 lines)
- [ ] `ui/WavyMusicSeekBar.kt` (212 lines)
- [ ] `ui/WeatherRulesState.kt` (30 lines)
- [ ] `ui/components/BatteryVisual.kt` (341 lines)
- [ ] `util/FaceUnlockCoordinator.kt` (441 lines)

*dependency level 11*

- [ ] `data/SmartIslandSettingsRepository.kt` (1746 lines)
- [ ] `ui/HostEdge.kt` (104 lines)
- [ ] `ui/components/BatteryCutoutBadge.kt` (195 lines)

*dependency level 12*

- [ ] `di/AshutoolEntryPoint.kt` (22 lines)
- [ ] `di/SmartIslandRepositories.kt` (42 lines)
- [ ] `ui/sections/UpdatesAndDownloadsSection.kt` (890 lines)

*dependency level 13*

- [ ] `model/LandscapeDemoPlan.kt` (215 lines)
- [ ] `ui/expanded/ExpandedActions.kt` (156 lines)
- [ ] `util/InAppStopwatch.kt` (122 lines)
- [ ] `util/InAppTimer.kt` (229 lines)

*dependency level 14*

- [ ] `data/SmartIslandSettings.kt` (1187 lines)
- [ ] `model/SwipeAction.kt` (47 lines)
- [ ] `ui/SliderSettingItem.kt` (144 lines)
- [ ] `ui/expanded/RecorderCard.kt` (289 lines)
- [ ] `util/ShadeActions.kt` (109 lines)
- [ ] `util/StaleCardSweep.kt` (27 lines)

## Stage 3 - services, data layer, island drawing


*dependency level 15*

- [ ] `MainActivity.kt` (76 lines)
- [ ] `model/LandscapeIdleDot.kt` (79 lines)
- [ ] `ui/FaceIdCutoutLayer.kt` (397 lines)
- [ ] `ui/FaceIdLayer.kt` (526 lines)
- [ ] `ui/IslandCallbacks.kt` (170 lines)
- [ ] `ui/IslandSwipeActions.kt` (199 lines)
- [ ] `ui/LandscapePillExit.kt` (278 lines)
- [ ] `ui/Theme.kt` (209 lines)
- [ ] `ui/expanded/AudioRecordingExpanded.kt` (35 lines)
- [ ] `ui/expanded/BatteryExpanded.kt` (230 lines)
- [ ] `ui/expanded/BluetoothExpanded.kt` (375 lines)
- [ ] `ui/expanded/DownloadExpanded.kt` (367 lines)
- [ ] `ui/expanded/FlashlightExpanded.kt` (138 lines)
- [ ] `ui/expanded/IncomingCallExpanded.kt` (191 lines)
- [ ] `ui/expanded/IslandPalette.kt` (114 lines)
- [ ] `ui/expanded/LiveActivityExpanded.kt` (293 lines)
- [ ] `ui/expanded/NavigationExpanded.kt` (282 lines)
- [ ] `ui/expanded/NotificationExpanded.kt` (656 lines)
- [ ] `ui/expanded/ScreenRecordingExpanded.kt` (32 lines)
- [ ] `ui/expanded/StopwatchExpanded.kt` (556 lines)
- [ ] `ui/expanded/TimerExpanded.kt` (676 lines)
- [ ] `ui/pickers/AppPickerDialog.kt` (295 lines)
- [ ] `ui/pickers/PickerActivity.kt` (137 lines)
- [ ] `ui/sections/AnimationSpeedSection.kt` (195 lines)
- [ ] `ui/sections/AppShortcutsSection.kt` (692 lines)
- [ ] `ui/sections/AppThemeOptions.kt` (222 lines)
- [ ] `ui/sections/BackupRestoreSection.kt` (570 lines)
- [ ] `ui/sections/CustomizationsSection.kt` (872 lines)
- [ ] `ui/sections/DemoSection.kt` (283 lines)
- [ ] `ui/sections/EventTogglesSection.kt` (428 lines)
- [ ] `ui/sections/ExemptAppsCard.kt` (160 lines)
- [ ] `ui/sections/FaceIdSettingsSection.kt` (111 lines)
- [ ] `ui/sections/FloatingWindowRow.kt` (129 lines)
- [ ] `ui/sections/GestureGuide.kt` (471 lines)
- [ ] `ui/sections/GestureSensitivityRow.kt` (100 lines)
- [ ] `ui/sections/GesturesSection.kt` (994 lines)
- [ ] `ui/sections/HapticStrengthRow.kt` (109 lines)
- [ ] `ui/sections/IdleScheduleCard.kt` (232 lines)
- [ ] `ui/sections/IdleUnitsSettings.kt` (130 lines)
- [ ] `ui/sections/IdleWidgetsSettingsSection.kt` (740 lines)
- [ ] `ui/sections/LandscapeSection.kt` (409 lines)
- [ ] `ui/sections/LedTickSettings.kt` (248 lines)
- [ ] `ui/sections/NetSpeedSettingsCard.kt` (169 lines)
- [ ] `ui/sections/PositionsSection.kt` (816 lines)
- [ ] `ui/sections/TimingSection.kt` (184 lines)
- [ ] `ui/sections/UtilitiesEditor.kt` (389 lines)
- [ ] `util/CameraCutoutDetector.kt` (153 lines)
- [ ] `util/ExpandedSwipe.kt` (56 lines)
- [ ] `util/LandscapeCameraDetector.kt` (83 lines)
- [ ] `util/LandscapeDemo.kt` (202 lines)
- [ ] `util/NotificationCooldownManager.kt` (180 lines)
- [ ] `util/SwipeArming.kt` (140 lines)

*dependency level 16*

- [ ] `service/SystemEventReceiver.kt` (448 lines)
- [ ] `ui/IslandActionRunner.kt` (62 lines)
- [ ] `ui/IslandOutsideTap.kt` (48 lines)
- [ ] `ui/IslandSmallViews.kt` (191 lines)
- [ ] `ui/IslandViewModel.kt` (1565 lines)
- [ ] `ui/expanded/IslandSurface.kt` (106 lines)
- [ ] `ui/sections/LandscapeLabSection.kt` (196 lines)
- [ ] `ui/sections/NotificationsAndPrivacySection.kt` (2032 lines)
- [ ] `util/ConnectivityEventMonitor.kt` (367 lines)
- [ ] `util/ShizukuManager.kt` (438 lines)
- [ ] `util/WiredDeviceEventMonitor.kt` (242 lines)

*dependency level 17*

- [ ] `ui/OverlayIsland.kt` (276 lines)
- [ ] `ui/sections/FaceUnlockSection.kt` (236 lines)
- [ ] `util/FaceUnlockWatcher.kt` (118 lines)
- [ ] `util/HotspotUtil.kt` (118 lines)
- [ ] `util/MediaOutputSwitcher.kt` (76 lines)
- [ ] `util/NotificationMediaReader.kt` (273 lines)

*dependency level 18*

- [ ] `service/SmartIslandNotificationListenerService.kt` (2401 lines)
- [ ] `ui/expanded/HotspotExpanded.kt` (232 lines)

*dependency level 19*

- [ ] `util/SystemServiceRecovery.kt` (103 lines)

*dependency level 20*

- [ ] `service/SmartIslandOverlayService.kt` (2258 lines)

*dependency level 21*

- [ ] `util/EnvironmentDiagnostics.kt` (102 lines)

*dependency level 22*

- [ ] `util/AppLogRecorder.kt` (303 lines)

## Stage 4 - island cards and the settings screens


*dependency level 23*

- [ ] `ui/sections/DeveloperOptionsSection.kt` (489 lines)
- [ ] `ui/sections/DiagnosticsSection.kt` (160 lines)
- [ ] `util/CustomFonts.kt` (140 lines)
- [ ] `util/IdleWidgetData.kt` (574 lines)
- [ ] `util/LogUtils.kt` (48 lines)
- [ ] `util/MediaOutput.kt` (151 lines)

*dependency level 24*

- [ ] `data/NotificationHistoryDbHelper.kt` (199 lines)
- [ ] `receiver/AutostartReceiver.kt` (58 lines)
- [ ] `ui/IdleWidgets.kt` (1652 lines)
- [ ] `ui/IslandFonts.kt` (138 lines)
- [ ] `ui/expanded/MediaShadeLayout.kt` (328 lines)
- [ ] `ui/sections/AboutSection.kt` (567 lines)
- [ ] `ui/sections/HeaderSection.kt` (100 lines)
- [ ] `ui/sections/IdleWidgetsSection.kt` (333 lines)
- [ ] `ui/sections/IslandFontMixSection.kt` (185 lines)
- [ ] `ui/sections/IslandFontPicker.kt` (230 lines)
- [ ] `ui/sections/SupportSection.kt` (132 lines)
- [ ] `ui/sections/WeatherSettingsCard.kt` (548 lines)
- [ ] `util/IntentUtils.kt` (40 lines)
- [ ] `util/MediaArt.kt` (204 lines)

*dependency level 25*

- [ ] `data/NotificationHistoryRepository.kt` (97 lines)
- [ ] `ui/IslandCollapsedContent.kt` (1237 lines)
- [ ] `ui/IslandInteractionState.kt` (194 lines)
- [ ] `ui/LedTickSlot.kt` (500 lines)
- [ ] `ui/expanded/MusicExpanded.kt` (418 lines)
- [ ] `util/NotificationViewInspector.kt` (279 lines)

*dependency level 26*

- [ ] `di/AppModule.kt` (43 lines)
- [ ] `ui/DayMoment.kt` (174 lines)
- [ ] `ui/FloatingWindowHandle.kt` (136 lines)
- [ ] `ui/HourChime.kt` (181 lines)
- [ ] `ui/IslandAutoHide.kt` (42 lines)
- [ ] `ui/IslandLayout.kt` (705 lines)
- [ ] `ui/IslandMainGestures.kt` (860 lines)
- [ ] `ui/IslandOverlayView.kt` (652 lines)
- [ ] `ui/TickPill.kt` (52 lines)
- [ ] `ui/WeatherTick.kt` (247 lines)
- [ ] `ui/sections/NotificationHistorySection.kt` (1563 lines)
- [ ] `util/ShizukuMethodCheck.kt` (134 lines)

*dependency level 27*

- [ ] `ui/IslandHiddenPillTarget.kt` (54 lines)
- [ ] `ui/IslandSecondary.kt` (456 lines)
- [ ] `ui/WidgetIsland.kt` (313 lines)
- [ ] `ui/sections/PermissionsSection.kt` (693 lines)
- [ ] `util/UtilityActions.kt` (533 lines)

*dependency level 28*

- [ ] `AshutoolBridge.kt` (154 lines)
- [ ] `ui/IslandCornerPill.kt` (208 lines)
- [ ] `ui/IslandHapticEffects.kt` (78 lines)
- [ ] `ui/IslandMergeBackdrop.kt` (146 lines)
- [ ] `ui/IslandPillSurface.kt` (194 lines)
- [ ] `ui/IslandSecondaryBubble.kt` (183 lines)
- [ ] `ui/IslandTertiaryPill.kt` (92 lines)
- [ ] `ui/SmartIslandHomeScreen.kt` (1569 lines)
- [ ] `ui/expanded/IslandExpandedContent.kt` (2066 lines)
- [ ] `ui/expanded/UtilitiesSection.kt` (785 lines)
- [ ] `ui/pickers/UtilityPickerDialogs.kt` (286 lines)

*dependency level 29*

- [ ] `ui/IslandCardHost.kt` (347 lines)

## Batches done

- Batch 1: 15 small files of Stage 1 (model and util), checked against the originals for identical public names and values.

## Thanks

(to be written at the end of Stage 5)
