# Firebase Analytics Android Screen Views Plan

All Screens & Modal Destinations in BinCheck (`com.aistudio.bincheck.vqxkpm`):

| TaskID | Completed | Title | Description | File Path |
| :--- | :--- | :--- | :--- | :--- |
| 1 | [x] | Main Workspace (`ValidatorScreen`) | Compact panes (`Split`, `Console`, `Results`) and Expanded `TwoPaneWorkspace` | `app/src/main/java/com/example/ui/ValidatorScreen.kt` |
| 2 | [x] | Navigation Drawer | Modal navigation drawer (`NavigationDrawer`) | `app/src/main/java/com/example/ui/AppNavigationDrawer.kt` |
| 3 | [x] | Studio & Engine Settings | 3-tab bottom sheet (`Settings_Engine`, `Settings_Gateway`, `Settings_System`) | `app/src/main/java/com/example/ui/SettingsBottomSheet.kt` |
| 4 | [x] | BIN Inspector | BIN / IIN lookup modal bottom sheet (`BinInspector`) | `app/src/main/java/com/example/ui/BinLookupBottomSheet.kt` |
| 5 | [x] | List & Format Toolkit | 3-tab toolkit studio (`ListToolkit_Clean`, `ListToolkit_Format`, `ListToolkit_Filter`) | `app/src/main/java/com/example/ui/ListToolkitBottomSheet.kt` |
| 6 | [x] | Custom Config Studio | 5-tab custom HTTP studio (`ConfigStudio_Request`, `ConfigStudio_Parse`, `ConfigStudio_Checkpoints`, `ConfigStudio_Proxy`, `ConfigStudio_Profiles`) | `app/src/main/java/com/example/ui/CustomConfigStudioBottomSheet.kt` |
| 7 | [x] | File Import | `.txt` / `.html` SAF importer sheet (`FileImport`) | `app/src/main/java/com/example/ui/FileImportBottomSheet.kt` |
| 8 | [x] | Recent Checks History | Paginated SQLite validation history sheet (`HistoryLog`) | `app/src/main/java/com/example/ui/HistoryLogBottomSheet.kt` |
