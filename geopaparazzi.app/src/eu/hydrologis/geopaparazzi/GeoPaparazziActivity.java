/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2010  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.hydrologis.geopaparazzi;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SlidingDrawer;
import android.widget.Toast;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.database.GPLogPreferencesHandler;
import eu.geopaparazzi.library.gps.GpsLocation;
import eu.geopaparazzi.library.gps.GpsManager;
import eu.geopaparazzi.library.sensors.SensorsManager;
import eu.geopaparazzi.library.sms.SmsData;
import eu.geopaparazzi.library.sms.SmsUtilities;
import eu.geopaparazzi.library.util.FileUtilities;
import eu.geopaparazzi.library.util.LibraryConstants;
import eu.geopaparazzi.library.util.PositionUtilities;
import eu.geopaparazzi.library.util.ResourcesManager;
import eu.geopaparazzi.library.util.Utilities;
import eu.geopaparazzi.library.util.activities.AboutActivity;
import eu.geopaparazzi.library.util.activities.DirectoryBrowserActivity;
import eu.geopaparazzi.spatialite.database.spatial.SpatialDatabasesManager;
import eu.geopaparazzi.spatialite.database.spatial.core.SpatialRasterTable;
import eu.hydrologis.geopaparazzi.dashboard.ActionBar;
import eu.hydrologis.geopaparazzi.dashboard.quickaction.dashboard.ActionItem;
import eu.hydrologis.geopaparazzi.dashboard.quickaction.dashboard.QuickAction;
import eu.hydrologis.geopaparazzi.database.DaoBookmarks;
import eu.hydrologis.geopaparazzi.database.DaoGpsLog;
import eu.hydrologis.geopaparazzi.database.DaoImages;
import eu.hydrologis.geopaparazzi.database.DaoNotes;
import eu.hydrologis.geopaparazzi.database.DatabaseManager;
import eu.hydrologis.geopaparazzi.database.NoteType;
import eu.hydrologis.geopaparazzi.maps.DataManager;
import eu.hydrologis.geopaparazzi.maps.LogMapItem;
import eu.hydrologis.geopaparazzi.maps.MapsActivity;
import eu.hydrologis.geopaparazzi.maps.tiles.MapGeneratorInternal;
import eu.hydrologis.geopaparazzi.osm.OsmUtilities;
import eu.hydrologis.geopaparazzi.preferences.PreferencesActivity;
import eu.hydrologis.geopaparazzi.util.Constants;
import eu.hydrologis.geopaparazzi.util.ExportActivity;
import eu.hydrologis.geopaparazzi.util.GpUtilities;
import eu.hydrologis.geopaparazzi.util.ImportActivity;
import eu.hydrologis.geopaparazzi.util.QuickActionsFactory;
import eu.hydrologis.geopaparazzi.util.SecretActivity;

/**
 * The main {@link Activity activity} of GeoPaparazzi.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class GeoPaparazziActivity extends Activity {

    private static final int MENU_ABOUT = Menu.FIRST;
    private static final int MENU_EXIT = 2;
    private static final int MENU_TILE_SOURCE_ID = 3;
    private static final int MENU_SETTINGS = 4;
    private static final int MENU_RESET = 5;
    private static final int MENU_LOAD = 6;

    private ResourcesManager resourcesManager;
    private ActionBar actionBar;

    private final int RETURNCODE_BROWSE_FOR_NEW_PREOJECT = 665;
    private final int RETURNCODE_NOTES = 666;
    private final int RETURNCODE_PICS = 667;
    private final int RETURNCODE_SKETCH = 668;

    private boolean sliderIsOpen = false;
    private GpsManager gpsManager;
    private SensorsManager sensorManager;
    private HashMap<Integer, String> tileSourcesMap = null;
    private HashMap<String, String> fileSourcesMap = null;
    private HashMap<String, SpatialRasterTable> rasterSourcesMap = null;
    private AlertDialog mapChoiceDialog;

    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);

        try {
            SpatialDatabasesManager.s_log_tag="mj10777";
             // SpatialDatabasesManager.s_log_tag=Constants.GEOPAPARAZZI;
            SpatialDatabasesManager.i_log_debug=1;
            initializeResourcesManager();

            fileSourcesMap = new HashMap<String, String>();
            rasterSourcesMap = new HashMap<String, SpatialRasterTable>();

            tileSourcesMap = new LinkedHashMap<Integer, String>();
            tileSourcesMap.put(1001, MapGeneratorInternal.DATABASE_RENDERER.name());
            tileSourcesMap.put(1002, MapGeneratorInternal.MAPNIK.name());
            tileSourcesMap.put(1003, MapGeneratorInternal.OPENCYCLEMAP.name());
            File mapsDir = ResourcesManager.getInstance(this).getMapsDir();
            int i = 1004;
            if (mapsDir != null && mapsDir.exists())
            {
                String s_extention=".mapurl";
                List<File> search_files=new ArrayList<File>();
                FileUtilities.search_directory_recursive(mapsDir,s_extention,search_files) ;
                Collections.sort(search_files);
                // SpatialDatabasesManager.app_log(-1,"GeoGeomCollActivity.onCreate: "+s_extention+"["+search_files.size()+"]");
                for( File file : search_files)
                {
                 String name = FileUtilities.getNameWithoutExtention(file);
                 // SpatialDatabasesManager.app_log(-1,"GeoGeomCollActivity.onCreate: "+s_extention+"["+name+"]");
                 tileSourcesMap.put(i++, name);
                 fileSourcesMap.put(name, file.getAbsolutePath());
                }
                search_files.clear();
                s_extention=".map";
                FileUtilities.search_directory_recursive(mapsDir,s_extention,search_files) ;
                Collections.sort(search_files);
                // SpatialDatabasesManager.app_log(-1,"GeoGeomCollActivity.onCreate: "+s_extention+"["+search_files.size()+"]");
                for( File file : search_files)
                {
                 String name = FileUtilities.getNameWithoutExtention(file);
                 // SpatialDatabasesManager.app_log(-1,"GeoGeomCollActivity.onCreate: "+s_extention+"["+name+"]");
                 tileSourcesMap.put(i++, name);
                 fileSourcesMap.put(name, file.getAbsolutePath());
                }
                /*
                 * add also geopackage tables
                 */
                try {
                    List<SpatialRasterTable> spatialRasterTables = SpatialDatabasesManager.getInstance().getSpatialRasterTables(
                            false);
                    for( SpatialRasterTable table : spatialRasterTables ) {
                        tileSourcesMap.put(i++, table.getTableName());
                        rasterSourcesMap.put(table.getTableName(), table);
                    }
                } catch (jsqlite.Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        checkIncomingGeosms();
        checkIncomingSmsData();

    }

    @SuppressWarnings("nls")
    /**
     * Checks if it was opened for a link of the kind:<br>
     * http://maps.google.com/maps?q=46.068941,11.169849&GeoSMS<br>
     * in which case the point is imported as bookmark.
     */
    private void checkIncomingGeosms() {
        Uri data = getIntent().getData();
        if (data != null) {
            try {
                String path = data.toString();
                if (path.toLowerCase().contains(SmsUtilities.SMSHOST)) {
                    return;
                }
                if (path.toLowerCase().contains("geosms") && path.toLowerCase().contains("q=")
                        && !path.toLowerCase().contains(SmsUtilities.SMSHOST)) {
                    String scheme = data.getScheme(); // "http"
                    if (scheme != null && scheme.equals("http")) {
                        String host = data.getHost();
                        if (host.equals("maps.google.com")) {
                            String pParameter = data.getQueryParameter("q");
                            String[] split = pParameter.split(",");
                            double lat = Double.parseDouble(split[0]);
                            double lon = Double.parseDouble(split[1]);
                            DaoBookmarks.addBookmark(lon, lat, "GeoSMS position", 16, -1, -1, -1, -1);
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                            PositionUtilities.putMapCenterInPreferences(preferences, lon, lat, 16);
                            Intent mapIntent = new Intent(this, MapsActivity.class);
                            startActivity(mapIntent);
                        }
                    }
                } else {
                    throw new IOException();
                }
            } catch (IOException e) {
                Utilities
                        .messageDialog(
                                this,
                                "Could not open the passed URI. Geopaparazzi is able to open only GeoSMS URIs that contain a part like: ...&q=46.068941,11.169849&GeoSMS ",
                                null);
            }
        }
    }

    private void checkIncomingSmsData() {
        /*
         * check if it was opened for a link of the kind
         *
         * http://maps.google.com/maps?q=46.068941,11.169849&GeoSMS
         */
        Uri data = getIntent().getData();
        if (data != null) {
            try {
                String path = data.toString();
                String scheme = data.getScheme(); // "http"
                if (scheme != null && scheme.equals("http")) { //$NON-NLS-1$
                    String host = data.getHost();
                    if (host.equals(SmsUtilities.SMSHOST)) {

                        List<SmsData> sms2Data = SmsUtilities.sms2Data(path);
                        int notesNum = 0;
                        int bookmarksNum = 0;
                        if (sms2Data.size() > 0) {
                            for( SmsData smsData : sms2Data ) {
                                String text = smsData.text.replaceAll("\\_", " "); //$NON-NLS-1$//$NON-NLS-2$
                                if (smsData.TYPE == SmsData.NOTE) {
                                    DaoNotes.addNote(smsData.x, smsData.y, smsData.z, new java.sql.Date(new Date().getTime()),
                                            text, NoteType.POI.getDef(), null, NoteType.POI.getTypeNum());
                                    notesNum++;
                                } else if (smsData.TYPE == SmsData.BOOKMARK) {
                                    DaoBookmarks.addBookmark(smsData.x, smsData.y, text, smsData.z, -1, -1, -1, -1);
                                    bookmarksNum++;
                                }
                            }
                        }

                        Utilities.messageDialog(this, MessageFormat.format(
                                getString(eu.hydrologis.geopaparazzi.R.string.imported_notes_and_bookmarks), notesNum,
                                bookmarksNum), null);
                    }
                }
            } catch (Exception e) {
                Utilities.messageDialog(this, getString(eu.hydrologis.geopaparazzi.R.string.could_not_open_sms), null);
            }
        }
    }

    protected void onResume() {
        super.onResume();
        checkActionBar();
    }

    public void onWindowFocusChanged( boolean hasFocus ) {
        super.onWindowFocusChanged(hasFocus);
        checkActionBar();
    }

    private void checkActionBar() {
        if (actionBar == null) {
            actionBar = ActionBar.getActionBar(this, R.id.action_bar, gpsManager, sensorManager);
            actionBar.setTitle(R.string.app_name, R.id.action_bar_title);
        }
        // actionBar.checkLogging();
    }

    private void initializeResourcesManager() throws Exception {
        Object stateObj = getLastNonConfigurationInstance();
        if (stateObj instanceof ResourcesManager) {
            resourcesManager = (ResourcesManager) stateObj;
        } else {
            ResourcesManager.resetManager();
            resourcesManager = ResourcesManager.getInstance(this);
        }

        if (resourcesManager == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(eu.hydrologis.geopaparazzi.R.string.no_sdcard_use_internal_memory).setCancelable(false)
                    .setPositiveButton(this.getString(android.R.string.yes), new DialogInterface.OnClickListener(){
                        public void onClick( DialogInterface dialog, int id ) {
                            ResourcesManager.setUseInternalMemory(true);
                            try {
                                resourcesManager = ResourcesManager.getInstance(GeoPaparazziActivity.this);
                                initIfOk();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).setNegativeButton(this.getString(android.R.string.no), new DialogInterface.OnClickListener(){
                        public void onClick( DialogInterface dialog, int id ) {
                            finish();
                        }
                    });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        } else {
            initIfOk();
        }

    }

    private void initIfOk() {
        if (resourcesManager == null) {
            Utilities.messageDialog(this, R.string.sdcard_notexist, new Runnable(){
                public void run() {
                    finish();
                }
            });
            return;
        }
        setContentView(R.layout.geopap_main);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        GPLogPreferencesHandler.checkLog(preferences);
        GPLogPreferencesHandler.checkLogHeavy(preferences);
        GPLogPreferencesHandler.checkLogAbsurd(preferences);

        try {
            DatabaseManager.getInstance().getDatabase();
            checkMapsAndLogsVisibility();

            SpatialDatabasesManager.reset();
            File mapsDir = ResourcesManager.getInstance(this).getMapsDir();
            SpatialDatabasesManager.getInstance().init(this, mapsDir);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), e.getLocalizedMessage(), e);
            e.printStackTrace();
            Utilities.toast(this, R.string.databaseError, Toast.LENGTH_LONG);
        }

        gpsManager = GpsManager.getInstance(this);
        sensorManager = SensorsManager.getInstance(this);

        checkActionBar();

        /*
         * the buttons
         */
        final int notesButtonId = R.id.dashboard_note_item_button;
        ImageButton notesButton = (ImageButton) findViewById(notesButtonId);
        notesButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(notesButtonId, v);
            }
        });

        final int undoNotesButtonId = R.id.dashboard_undonote_item_button;
        ImageButton undoNotesButton = (ImageButton) findViewById(undoNotesButtonId);
        undoNotesButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(undoNotesButtonId, v);
            }
        });

        final int logButtonId = R.id.dashboard_log_item_button;
        ImageButton logButton = (ImageButton) findViewById(logButtonId);
        // isChecked = applicationManager.isGpsLogging();
        logButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(logButtonId, v);
            }
        });

        final int mapButtonId = R.id.dashboard_map_item_button;
        ImageButton mapButton = (ImageButton) findViewById(mapButtonId);
        mapButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(mapButtonId, v);
            }
        });

        final int importButtonId = R.id.dashboard_import_item_button;
        ImageButton importButton = (ImageButton) findViewById(importButtonId);
        importButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(importButtonId, v);
            }
        });

        final int exportButtonId = R.id.dashboard_export_item_button;
        ImageButton exportButton = (ImageButton) findViewById(exportButtonId);
        exportButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(exportButtonId, v);
            }
        });

        // slidingdrawer
        final int slidingId = R.id.slide;
        slidingDrawer = (SlidingDrawer) findViewById(slidingId);
        slidingDrawer.setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener(){
            public void onDrawerOpened() {
                sliderIsOpen = true;
            }
        });
        slidingDrawer.setOnDrawerCloseListener(new SlidingDrawer.OnDrawerCloseListener(){
            public void onDrawerClosed() {
                sliderIsOpen = false;
            }
        });

        // panic buttons part
        final int panicButtonId = R.id.panicbutton;
        Button panicButton = (Button) findViewById(panicButtonId);
        panicButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(panicButtonId, v);
            }
        });
        final int statusUpdateButtonId = R.id.statusupdatebutton;
        Button statusUpdateButton = (Button) findViewById(statusUpdateButtonId);
        statusUpdateButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ) {
                push(statusUpdateButtonId, v);
            }
        });

        boolean doOsmPref = preferences.getBoolean(Constants.PREFS_KEY_DOOSM, false);
        if (doOsmPref)
            OsmUtilities.handleOsmTagsDownload(this);

        Utilities.toast(this, getString(eu.hydrologis.geopaparazzi.R.string.loaded_project_in)
                + resourcesManager.getApplicationDir().getAbsolutePath(), Toast.LENGTH_LONG);

        // check for screen on
        boolean keepScreenOn = preferences.getBoolean(Constants.PREFS_KEY_SCREEN_ON, false);
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void push( int id, View v ) {
        switch( id ) {
        case R.id.dashboard_note_item_button: {
            QuickAction qa = new QuickAction(v);
            qa.addActionItem(QuickActionsFactory.INSTANCE.getNotesQuickAction(qa, this, RETURNCODE_NOTES));
            qa.addActionItem(QuickActionsFactory.INSTANCE.getPicturesQuickAction(qa, this, RETURNCODE_PICS));
            qa.addActionItem(QuickActionsFactory.INSTANCE.getAudioQuickAction(qa, this, resourcesManager.getMediaDir()));
            qa.addActionItem(QuickActionsFactory.INSTANCE.getSketchQuickAction(qa, this, RETURNCODE_SKETCH));
            qa.setAnimStyle(QuickAction.ANIM_AUTO);
            qa.show();
            break;
        }
        case R.id.dashboard_undonote_item_button: {
            new AlertDialog.Builder(this).setTitle(R.string.text_undo_a_gps_note).setMessage(R.string.remove_last_note_prompt)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){
                        public void onClick( DialogInterface dialog, int whichButton ) {
                        }
                    }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                        public void onClick( DialogInterface dialog, int whichButton ) {
                            try {
                                DaoNotes.deleteLastInsertedNote();
                                Utilities.toast(GeoPaparazziActivity.this, R.string.last_note_deleted, Toast.LENGTH_LONG);
                            } catch (IOException e) {
                                GPLog.error(this, e.getLocalizedMessage(), e);
                                e.printStackTrace();
                                Utilities.toast(GeoPaparazziActivity.this, R.string.last_note_not_deleted, Toast.LENGTH_LONG);
                            }
                        }
                    }).show();

            break;
        }
        case R.id.dashboard_log_item_button: {
            QuickAction qa = new QuickAction(v);
            if (gpsManager.isDatabaseLogging()) {
                ActionItem stopLogQuickAction = QuickActionsFactory.INSTANCE.getStopLogQuickAction(actionBar, qa, this);
                qa.addActionItem(stopLogQuickAction);
            } else {
                ActionItem startLogQuickAction = QuickActionsFactory.INSTANCE.getStartLogQuickAction(actionBar, qa, this);
                qa.addActionItem(startLogQuickAction);
            }
            qa.setAnimStyle(QuickAction.ANIM_AUTO);
            qa.show();
            break;
        }
        case R.id.dashboard_map_item_button: {
            Intent mapIntent = new Intent(this, MapsActivity.class);
            startActivity(mapIntent);
            break;
        }
        case R.id.dashboard_import_item_button: {
            Intent exportIntent = new Intent(this, ImportActivity.class);
            startActivity(exportIntent);
            break;
        }
        case R.id.dashboard_export_item_button: {
            Intent exportIntent = new Intent(this, ExportActivity.class);
            startActivity(exportIntent);
            break;
        }
        case R.id.panicbutton: {
            sendPosition(true);
            break;
        }
        case R.id.statusupdatebutton: {
            sendPosition(false);
            break;
        }
        default:
            break;
        }
    }

    // public boolean onPrepareOptionsMenu (Menu menu) {
    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_SETTINGS, 0, R.string.mainmenu_preferences).setIcon(android.R.drawable.ic_menu_preferences);
        final SubMenu subMenu = menu.addSubMenu(Menu.NONE, MENU_TILE_SOURCE_ID, 1, R.string.mapsactivity_menu_tilesource)
                .setIcon(R.drawable.ic_menu_tilesource);
        {
            Set<Entry<Integer, String>> entrySet = tileSourcesMap.entrySet();
            for( Entry<Integer, String> entry : entrySet ) {
                subMenu.add(0, entry.getKey(), Menu.NONE, entry.getValue());
            }
        }
        menu.add(Menu.NONE, MENU_RESET, 2, R.string.reset).setIcon(android.R.drawable.ic_menu_revert);
        menu.add(Menu.NONE, MENU_LOAD, 3, R.string.load).setIcon(android.R.drawable.ic_menu_set_as);
        menu.add(Menu.NONE, MENU_EXIT, 4, R.string.exit).setIcon(android.R.drawable.ic_lock_power_off);
        menu.add(Menu.NONE, MENU_ABOUT, 5, R.string.about).setIcon(android.R.drawable.ic_menu_info_details);

        return true;
    }

    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        switch( item.getItemId() ) {
        case MENU_ABOUT:
            Intent intent = new Intent(this, AboutActivity.class);
            intent.putExtra(LibraryConstants.PREFS_KEY_TEXT, "eu.hydrologis.geopaparazzi"); //$NON-NLS-1$
            startActivity(intent);
            return true;
        case MENU_SETTINGS:
            Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
            startActivity(preferencesIntent);
            return true;
        case MENU_RESET:
            resetData();
            return true;
        case MENU_LOAD:
            Intent browseIntent = new Intent(this, DirectoryBrowserActivity.class);
            browseIntent.putExtra(DirectoryBrowserActivity.EXTENTION, DirectoryBrowserActivity.FOLDER);
            browseIntent.putExtra(DirectoryBrowserActivity.STARTFOLDERPATH, resourcesManager.getApplicationDir()
                    .getAbsolutePath());
            startActivityForResult(browseIntent, RETURNCODE_BROWSE_FOR_NEW_PREOJECT);
            return true;
        case MENU_EXIT:
            finish();
            return true;
        case MENU_TILE_SOURCE_ID:
            return true;
        default: {
            String name = item.getTitle().toString();
            MapGeneratorInternal mapGeneratorInternalNew = null;
            try {
                mapGeneratorInternalNew = MapGeneratorInternal.valueOf(name);
            } catch (IllegalArgumentException e) {
                // ignore, is custom
            }
            if (mapGeneratorInternalNew != null) {
                if (mapGeneratorInternalNew.equals(MapGeneratorInternal.DATABASE_RENDERER)) {
                    // check existing maps and ask for which to load
                    File mapsDir = null;
                    try {
                        mapsDir = ResourcesManager.getInstance(this).getMapsDir();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (mapsDir == null || !mapsDir.exists()) {
                        Utilities.messageDialog(this, eu.hydrologis.geopaparazzi.R.string.no_external_sdcard_for_db_renderer,
                                null);
                        return true;
                    }

                    final List<String> mapPaths = new ArrayList<String>();
                    final List<String> mapNames = new ArrayList<String>();

                    String s_extention=".map";
                    List<File> search_files=new ArrayList<File>();
                    FileUtilities.search_directory_recursive(mapsDir,s_extention,search_files);
                    s_extention=".mbtiles";
                    FileUtilities.search_directory_recursive(mapsDir,s_extention,search_files);
                    if (search_files.size() == 0)
                    {
                     Utilities.messageDialog(this,eu.hydrologis.geopaparazzi.R.string.no_map_files_found_go_online, null);
                     return true;
                    }
                    else
                    {
                     Collections.sort(search_files);
                     for( File file : search_files)
                     {
                      String file_name = FileUtilities.getNameWithoutExtention(file);
                      // SpatialDatabasesManager.app_log(-1,"GeoGeomCollActivity.onMenuItemSelected: "+s_extention+"["+file_name+"]");
                      mapPaths.add(file.getAbsolutePath());
                      mapNames.add(file_name);
                     }
                    }
                    String[] mapNamesArrays = mapNames.toArray(new String[0]);
                    boolean[] mapNamesChecked = new boolean[mapNamesArrays.length];
                    DialogInterface.OnMultiChoiceClickListener dialogListener = new DialogInterface.OnMultiChoiceClickListener(){
                        public void onClick( DialogInterface dialog, int which, boolean isChecked ) {
                            String mapPath = mapPaths.get(which);
                            setTileSource(MapGeneratorInternal.DATABASE_RENDERER.toString(), new File(mapPath));
                            mapChoiceDialog.dismiss();
                        }
                    };

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(eu.hydrologis.geopaparazzi.R.string.select_map_to_use);
                    builder.setMultiChoiceItems(mapNamesArrays, mapNamesChecked, dialogListener);
                    mapChoiceDialog = builder.create();
                    mapChoiceDialog.show();
                } else {
                    setTileSource(mapGeneratorInternalNew.toString(), null);
                }
            } else {

                String fileSource = fileSourcesMap.get(name);
                if (fileSource != null) {
                    setTileSource(null, new File(fileSource));
                } else {
                    // try raster
                    SpatialRasterTable spatialRasterTable = rasterSourcesMap.get(name);
                    if (spatialRasterTable != null) {
                        setTileSource(spatialRasterTable);
                    }
                }

            }
        }
        }
        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * Sets the tilesource.
     *
     * <p>
     * If both arguments are set null, it will try to get info from the preferences,
     * and used sources are saved into preferences.
     * </p>
     *
     * @param sourceName if source is <code>null</code>, mapnik is used.
     * @param mapfile the map file to use in case it is a database based source.
     */
    private void setTileSource( String sourceName, File mapfile ) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        MapGeneratorInternal mapGeneratorInternal = MapGeneratorInternal.MAPNIK;
        if (sourceName != null) {
            mapGeneratorInternal = MapGeneratorInternal.valueOf(sourceName);

            if (mapGeneratorInternal.equals(MapGeneratorInternal.DATABASE_RENDERER)) {
                if (mapfile == null || !mapfile.exists()) {
                    // try from preferences
                    String filePath = preferences.getString(Constants.PREFS_KEY_TILESOURCE_FILE, ""); //$NON-NLS-1$
                    mapfile = new File(filePath);
                    if (!mapfile.exists()) {
                        mapGeneratorInternal = MapGeneratorInternal.MAPNIK;
                        Utilities
                                .messageDialog(this, eu.hydrologis.geopaparazzi.R.string.no_map_file_found_going_to_mapnik, null);
                        mapfile = null;
                    }
                }
            }
        }

        Editor editor = preferences.edit();
        editor.putString(Constants.PREFS_KEY_TILESOURCE, sourceName);
        if (mapfile != null)
            editor.putString(Constants.PREFS_KEY_TILESOURCE_FILE, mapfile.getAbsolutePath());
        editor.commit();

    }

    private void setTileSource( SpatialRasterTable table ) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = preferences.edit();
        editor.putString(Constants.PREFS_KEY_TILESOURCE, table.getTableName());
        editor.commit();
    }

    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult(requestCode, resultCode, data);
        switch( requestCode ) {
        case (RETURNCODE_BROWSE_FOR_NEW_PREOJECT): {
            if (resultCode == Activity.RESULT_OK) {
                String chosenFolderToLoad = data.getStringExtra(LibraryConstants.PREFS_KEY_PATH);
                if (chosenFolderToLoad != null && new File(chosenFolderToLoad).getParentFile().exists()) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                    Editor editor = preferences.edit();
                    editor.putString(LibraryConstants.PREFS_KEY_BASEFOLDER, chosenFolderToLoad);
                    editor.commit();

                    DatabaseManager.getInstance().closeDatabase();
                    ResourcesManager.resetManager();

                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                }
            }
            break;
        }
        case (RETURNCODE_NOTES): {
            if (resultCode == Activity.RESULT_OK) {
                String[] noteArray = data.getStringArrayExtra(LibraryConstants.PREFS_KEY_NOTE);
                if (noteArray != null) {
                    try {
                        double lon = Double.parseDouble(noteArray[0]);
                        double lat = Double.parseDouble(noteArray[1]);
                        double elev = Double.parseDouble(noteArray[2]);
                        Date date = LibraryConstants.TIME_FORMATTER.parse(noteArray[3]);
                        DaoNotes.addNote(lon, lat, elev, new java.sql.Date(date.getTime()), noteArray[4], NoteType.POI.getDef(),
                                null, NoteType.POI.getTypeNum());
                    } catch (Exception e) {
                        e.printStackTrace();
                        Utilities.messageDialog(this, eu.geopaparazzi.library.R.string.notenonsaved, null);
                    }
                }
            }
            break;
        }
        case (RETURNCODE_PICS): {
            if (resultCode == Activity.RESULT_OK) {
                String relativeImagePath = data.getStringExtra(LibraryConstants.PREFS_KEY_PATH);
                if (relativeImagePath != null) {
                    File imgFile = new File(resourcesManager.getMediaDir().getParentFile(), relativeImagePath);
                    if (!imgFile.exists()) {
                        return;
                    }
                    try {
                        double lat = data.getDoubleExtra(LibraryConstants.LATITUDE, 0.0);
                        double lon = data.getDoubleExtra(LibraryConstants.LONGITUDE, 0.0);
                        double elev = data.getDoubleExtra(LibraryConstants.ELEVATION, 0.0);
                        double azim = data.getDoubleExtra(LibraryConstants.AZIMUTH, 0.0);
                        DaoImages.addImage(lon, lat, elev, azim, new java.sql.Date(new Date().getTime()), "", relativeImagePath);
                    } catch (Exception e) {
                        e.printStackTrace();

                        Utilities.messageDialog(this, eu.geopaparazzi.library.R.string.notenonsaved, null);
                    }
                }
            }
            break;
        }
        case (RETURNCODE_SKETCH): {
            if (data != null) {
                String absoluteImagePath = data.getStringExtra(LibraryConstants.PREFS_KEY_PATH);
                if (absoluteImagePath != null) {
                    File imgFile = new File(absoluteImagePath);
                    if (!imgFile.exists()) {
                        return;
                    }
                    try {
                        double lat = data.getDoubleExtra(LibraryConstants.LATITUDE, 0.0);
                        double lon = data.getDoubleExtra(LibraryConstants.LONGITUDE, 0.0);
                        double elev = data.getDoubleExtra(LibraryConstants.ELEVATION, 0.0);

                        DaoImages.addImage(lon, lat, elev, -9999.0, new java.sql.Date(new Date().getTime()), "",
                                absoluteImagePath);
                    } catch (Exception e) {
                        e.printStackTrace();

                        Utilities.messageDialog(this, eu.geopaparazzi.library.R.string.notenonsaved, null);
                    }
                }
            }
            break;
        }
        }
    }

    private int backCount = 0;
    private long previousBackTime = System.currentTimeMillis();
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        // force to exit through the exit button
        // System.out.println(keyCode + "/" + KeyEvent.KEYCODE_BACK);
        switch( keyCode ) {
        case KeyEvent.KEYCODE_BACK:
            if (sliderIsOpen) {
                slidingDrawer.animateClose();
            }
            backCount++;
            long backTime = System.currentTimeMillis();

            if (backTime - previousBackTime < 1000) {
                if (backCount > 10) {
                    Intent hiddenIntent = new Intent(this, SecretActivity.class);
                    startActivity(hiddenIntent);
                }
            } else {
                backCount = 0;
            }
            previousBackTime = backTime;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void finish() {
        try {
            if (actionBar != null)
                actionBar.cleanup();
            if (GPLog.LOG)
                Log.i("GEOPAPARAZZIACTIVITY", "Finish called!"); //$NON-NLS-1$
            // save last location just in case
            if (resourcesManager == null) {
                super.finish();
                return;
            }
            GpsLocation loc = gpsManager.getLocation();
            if (loc != null) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                PositionUtilities.putGpsLocationInPreferences(preferences, loc.getLongitude(), loc.getLatitude(),
                        loc.getAltitude());
            }
            Utilities.toast(this, R.string.loggingoff, Toast.LENGTH_LONG);
            gpsManager.dispose(this);
            try {
                SpatialDatabasesManager.getInstance().closeDatabases();
            } catch (Exception e) {
                e.printStackTrace();
            }
            DatabaseManager.getInstance().closeDatabase();
            ResourcesManager.resetManager();
            resourcesManager = null;
        } finally {
            super.finish();
        }
    }

    private AlertDialog alertDialog = null;
    private void resetData() {
        final String enterNewProjectString = getString(eu.hydrologis.geopaparazzi.R.string.enter_a_name_for_the_new_project);
        final String projectExistingString = getString(eu.hydrologis.geopaparazzi.R.string.chosen_project_exists);

        final File applicationParentDir = resourcesManager.getApplicationParentDir();
        final String newGeopaparazziDirName = Constants.GEOPAPARAZZI
                + "_" + LibraryConstants.TIMESTAMPFORMATTER.format(new Date()); //$NON-NLS-1$
        final EditText input = new EditText(this);
        input.setText(newGeopaparazziDirName);
        input.addTextChangedListener(new TextWatcher(){
            public void onTextChanged( CharSequence s, int start, int before, int count ) {
            }
            public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
            }
            public void afterTextChanged( Editable s ) {
                String newName = s.toString();
                File newProjectFile = new File(applicationParentDir, newName);
                if (newName == null || newName.length() < 1) {
                    alertDialog.setMessage(enterNewProjectString);
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                } else if (newProjectFile.exists()) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    alertDialog.setMessage(projectExistingString);
                } else {
                    alertDialog.setMessage(enterNewProjectString);
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }
        });
        Builder builder = new AlertDialog.Builder(this).setTitle(R.string.reset);
        builder.setMessage(enterNewProjectString);
        builder.setView(input);
        alertDialog = builder.setIcon(android.R.drawable.ic_dialog_alert)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){
                    public void onClick( DialogInterface dialog, int whichButton ) {
                    }
                }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                    public void onClick( DialogInterface dialog, int whichButton ) {
                        try {
                            Editable value = input.getText();
                            String newName = value.toString();
                            DatabaseManager.getInstance().closeDatabase();
                            File newGeopaparazziDirFile = new File(applicationParentDir.getAbsolutePath(), newName);
                            if (!newGeopaparazziDirFile.mkdir()) {
                                throw new IOException("Unable to create the geopaparazzi folder."); //$NON-NLS-1$
                            }
                            ResourcesManager.getInstance(GeoPaparazziActivity.this).setApplicationDir(GeoPaparazziActivity.this,
                                    newGeopaparazziDirFile.getAbsolutePath());

                            Intent intent = getIntent();
                            finish();
                            startActivity(intent);
                        } catch (Exception e) {
                            GPLog.error(this, e.getLocalizedMessage(), e);
                            e.printStackTrace();
                            Toast.makeText(GeoPaparazziActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }).setCancelable(false).create();
        alertDialog.show();

    }

    private SlidingDrawer slidingDrawer;

    private void checkMapsAndLogsVisibility() throws IOException {
        List<LogMapItem> maps = DaoGpsLog.getGpslogs();
        boolean oneVisible = false;
        for( LogMapItem item : maps ) {
            if (!oneVisible && item.isVisible()) {
                oneVisible = true;
            }
        }
        DataManager.getInstance().setLogsVisible(oneVisible);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return resourcesManager;
    }

    /**
     * Send the panic or status update message.
     */
    @SuppressWarnings("nls")
    private void sendPosition( boolean isPanic ) {
        if (isPanic) {
            String lastPosition = getString(R.string.help_needed);
            String[] panicNumbers = GpUtilities.getPanicNumbers(this);
            if (panicNumbers == null) {
                String positionText = SmsUtilities.createPositionText(this, lastPosition);
                SmsUtilities.sendSMSViaApp(this, "", positionText);
            } else {
                for( String number : panicNumbers ) {
                    number = number.trim();
                    if (number.length() == 0) {
                        continue;
                    }

                    String positionText = SmsUtilities.createPositionText(this, lastPosition);
                    SmsUtilities.sendSMS(this, number, positionText, true);
                }
            }
        } else {
            // just sending a single geosms
            String positionText = SmsUtilities.createPositionText(this, "");
            SmsUtilities.sendSMSViaApp(this, "", positionText);
        }

    }

}
