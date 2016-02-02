/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2016  HydroloGIS (www.hydrologis.com)
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
package eu.geopaparazzi.mapsforge.mapsdirmanager;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.mapgenerator.MapGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import eu.geopaparazzi.library.GPApplication;
import eu.geopaparazzi.library.core.ResourcesManager;
import eu.geopaparazzi.library.core.maps.BaseMap;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.util.LibraryConstants;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.CustomTileDatabasesManager;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.MapDatabasesManager;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.CustomTileDatabaseHandler;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.CustomTileTable;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.GeopackageTileDownloader;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.MapDatabaseHandler;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.MapGeneratorInternal;
import eu.geopaparazzi.mapsforge.mapsdirmanager.maps.tiles.MapTable;
import eu.geopaparazzi.mapsforge.mapsdirmanager.utils.DefaultMapurls;
import eu.geopaparazzi.spatialite.database.spatial.SpatialDatabasesManager;
import eu.geopaparazzi.spatialite.database.spatial.core.daos.SPL_Vectors;
import eu.geopaparazzi.spatialite.database.spatial.core.databasehandlers.AbstractSpatialDatabaseHandler;
import eu.geopaparazzi.spatialite.database.spatial.core.enums.SpatialDataType;
import eu.geopaparazzi.spatialite.database.spatial.core.enums.VectorLayerQueryModes;
import eu.geopaparazzi.spatialite.database.spatial.core.tables.AbstractSpatialTable;
import eu.geopaparazzi.spatialite.database.spatial.core.tables.SpatialRasterTable;
import eu.geopaparazzi.spatialite.database.spatial.util.SpatialiteLibraryConstants;
import jsqlite.Exception;

/**
 * The base maps sources manager.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public enum BaseMapSourcesManager {
    INSTANCE;

    private SharedPreferences mPreferences;
    private List<BaseMap> mBaseMaps;

    private String selectedTileSourceType = "";
    private String selectedTableDatabasePath = "";
    private String selectedTableTitle = "";
    private AbstractSpatialTable selectedBaseMapTable = null;

    private HashMap<BaseMap, AbstractSpatialTable> mBaseMaps2TablesMap = new HashMap<>();

    private File mMapnikFile;

    private boolean mReReadBasemaps = true;

    BaseMapSourcesManager() {

        try {
            GPApplication gpApplication = GPApplication.getInstance();
            mPreferences = PreferenceManager.getDefaultSharedPreferences(gpApplication);
            /*
             * if they do not exist add two mapurl based mapnik and opencycle
             * tile sources as default ones. They will automatically
             * be backed into a mbtiles db.
            */
            File applicationSupporterDir = ResourcesManager.getInstance(gpApplication).getApplicationSupporterDir();
            mMapnikFile = new File(applicationSupporterDir, DefaultMapurls.Mapurls.mapnik.toString() + DefaultMapurls.MAPURL_EXTENSION);
            DefaultMapurls.checkAllSourcesExistence(gpApplication, applicationSupporterDir);

            boolean doSpatialiteRecoveryMode = mPreferences.getBoolean(SpatialiteLibraryConstants.PREFS_KEY_SPATIALITE_RECOVERY_MODE,
                    false);
            // doSpatialiteRecoveryMode=true;
            if (doSpatialiteRecoveryMode) {
                // Turn on Spatialite Recovery Modus
                SPL_Vectors.VECTORLAYER_QUERYMODE = VectorLayerQueryModes.CORRECTIVEWITHINDEX;
                // and reset it in the preferences
                Editor editor = mPreferences.edit();
                editor.putBoolean(SpatialiteLibraryConstants.PREFS_KEY_SPATIALITE_RECOVERY_MODE, false);
                editor.apply();
            }
            selectedTileSourceType = mPreferences.getString(LibraryConstants.PREFS_KEY_TILESOURCE, ""); //$NON-NLS-1$
            selectedTableDatabasePath = mPreferences.getString(LibraryConstants.PREFS_KEY_TILESOURCE_FILE, ""); //$NON-NLS-1$
            selectedTableTitle = mPreferences.getString(LibraryConstants.PREFS_KEY_TILESOURCE_TITLE, ""); //$NON-NLS-1$

            List<BaseMap> baseMaps = getBaseMaps();
            if (selectedTableDatabasePath.length() == 0 || !new File(selectedTableDatabasePath).exists()) {
                // select mapnik by default
                for (BaseMap baseMap : baseMaps) {
                    if (baseMap.databasePath.equals(mMapnikFile.getAbsolutePath())) {
                        selectedTableDatabasePath = baseMap.databasePath;
                        selectedTableTitle = baseMap.title;
                        selectedTileSourceType = baseMap.mapType;

                        setTileSource(selectedTileSourceType, selectedTableDatabasePath, selectedTableTitle);
                        selectedBaseMapTable = mBaseMaps2TablesMap.get(baseMap);
                        break;
                    }
                }
            } else {
                for (BaseMap baseMap : baseMaps) {
                    if (baseMap.databasePath.equals(selectedTableDatabasePath)) {
                        selectedBaseMapTable = mBaseMaps2TablesMap.get(baseMap);
                        break;
                    }
                }
            }

        } catch (java.lang.Exception e) {
            GPLog.error(this, null, e);
        }

    }

    /**
     * Getter for the current available basemaps.
     *
     * @return the list of basemaps.
     */
    public List<BaseMap> getBaseMaps() {
        try {
            if (mBaseMaps == null || mReReadBasemaps) {
                mBaseMaps = getBaseMapsFromPreferences();

                if (mBaseMaps.size() == 0) {
                    addBaseMapFromFile(mMapnikFile);
                }
                mReReadBasemaps = false;
            }
            return mBaseMaps;
        } catch (java.lang.Exception e) {
            GPLog.error(this, null, e);
        }
        return Collections.emptyList();
    }

    /**
     * Reads the maps from preferences and extracts the tables necessary.
     *
     * @return the list of available BaseMaps.
     * @throws java.lang.Exception
     */
    private List<BaseMap> getBaseMapsFromPreferences() throws java.lang.Exception {
        String baseMapsJson = mPreferences.getString(BaseMap.BASEMAPS_PREF_KEY, "");
        List<BaseMap> baseMaps = BaseMap.fromJsonString(baseMapsJson);
        mBaseMaps2TablesMap.clear();

        // TODO this is ugly right now, needs to be changed
        for (BaseMap baseMap : baseMaps) {
            List<AbstractSpatialTable> tables = collectTablesFromFile(new File(baseMap.databasePath));
            for (AbstractSpatialTable table : tables) {
                BaseMap tmpBaseMap = table2BaseMap(table);
                if (!mBaseMaps2TablesMap.containsKey(tmpBaseMap))
                    mBaseMaps2TablesMap.put(tmpBaseMap, table);
            }
        }
        return baseMaps;
    }

    public void saveBaseMapsToPreferences(List<BaseMap> baseMaps) throws JSONException {
        String baseMapJson = BaseMap.toJsonString(baseMaps);
        Editor editor = mPreferences.edit();
        editor.putString(BaseMap.BASEMAPS_PREF_KEY, baseMapJson);
        editor.apply();
    }

    public boolean addBaseMapFromFile(File file) {
        boolean foundBaseMap = false;
        try {
            if (mBaseMaps == null) mBaseMaps = new ArrayList<>();

            List<AbstractSpatialTable> collectedTables = collectTablesFromFile(file);
            if (collectedTables.size() > 0) foundBaseMap = true;
            saveToBaseMap(collectedTables);
        } catch (java.lang.Exception e) {
            GPLog.error(this, null, e);
        }
        return foundBaseMap;
    }

    public void removeBaseMap(BaseMap baseMap) throws JSONException {
        mBaseMaps.remove(baseMap);
        mBaseMaps2TablesMap.remove(baseMap);
        saveBaseMapsToPreferences(mBaseMaps);
    }

    @NonNull
    private List<AbstractSpatialTable> collectTablesFromFile(File file) throws IOException, Exception {
        List<AbstractSpatialTable> collectedTables = new ArrayList<>();
            /*
             * add MAPURL TABLES
             */
        try (CustomTileDatabaseHandler customTileDatabaseHandler = CustomTileDatabasesManager.getInstance().getHandlerForFile(file)) {
            if (customTileDatabaseHandler != null) {
                List<CustomTileTable> tables = customTileDatabaseHandler.getTables(false);
                for (AbstractSpatialTable table : tables) {
                    collectedTables.add(table);
                }
            } else {
            /*
             * add MAP TABLES
             */
                MapDatabasesManager mapDatabasesManager = MapDatabasesManager.getInstance();
                try (MapDatabaseHandler mapDatabaseHandler = mapDatabasesManager.getHandlerForFile(file)) {
                    if (mapDatabaseHandler != null) {
                        List<MapTable> tables = mapDatabaseHandler.getTables(false);
                        for (AbstractSpatialTable table : tables) {
                            collectedTables.add(table);
                        }
                    } else {
                        /*
                         * add MBTILES, GEOPACKAGE, RASTERLITE TABLES
                         */
                        try (AbstractSpatialDatabaseHandler sdbHandler = SpatialDatabasesManager.getInstance().getRasterHandlerForFile(file)) {
                            List<SpatialRasterTable> tables = sdbHandler.getSpatialRasterTables(false);
                            for (AbstractSpatialTable table : tables) {
                                collectedTables.add(table);
                            }
                        }
                    }
                }
            }
        }
        return collectedTables;
    }

    private void saveToBaseMap(List<AbstractSpatialTable> tablesList) throws JSONException {
        for (AbstractSpatialTable table : tablesList) {
            BaseMap newBaseMap = table2BaseMap(table);
            mBaseMaps.add(newBaseMap);
            mBaseMaps2TablesMap.put(newBaseMap, table);
        }
        saveBaseMapsToPreferences(mBaseMaps);
    }

    @NonNull
    private BaseMap table2BaseMap(AbstractSpatialTable table) {
        BaseMap newBaseMap = new BaseMap();
        String databasePath = table.getDatabasePath();
        File databaseFile = new File(databasePath);
        newBaseMap.parentFolder = databaseFile.getParent();
        newBaseMap.databasePath = table.getDatabasePath();
        newBaseMap.mapType = table.getMapType();
        newBaseMap.title = table.getTitle();
        return newBaseMap;
    }


    /**
     * Getter for the current selected map table.
     *
     * @return the current selected map table.
     */
    public AbstractSpatialTable getSelectedBaseMapTable() {
        return selectedBaseMapTable;
    }

    /**
     * Selected a Map through its BaseMap.
     *
     * @param baseMap the base map to use..
     * @throws jsqlite.Exception
     */
    public void setSelectedBaseMap(BaseMap baseMap) throws Exception {
        selectedTileSourceType = baseMap.mapType;
        selectedTableDatabasePath = baseMap.databasePath;
        selectedTableTitle = baseMap.title;

        selectedBaseMapTable = mBaseMaps2TablesMap.get(baseMap);

        setTileSource(selectedTileSourceType, selectedTableDatabasePath, selectedTableTitle);
    }

    /**
     * Sets the tilesource for the map.
     */
    private void setTileSource(String selectedTileSourceType, String selectedTileSourceFile, String selectedTableTitle) {
        Editor editor = mPreferences.edit();
        editor.putString(LibraryConstants.PREFS_KEY_TILESOURCE, selectedTileSourceType);
        editor.putString(LibraryConstants.PREFS_KEY_TILESOURCE_FILE, selectedTileSourceFile);
        editor.putString(LibraryConstants.PREFS_KEY_TILESOURCE_TITLE, selectedTableTitle);
        editor.apply();
    }


    /**
     * Load the currently selected BaseMap.
     * <p/>
     * <p>This method should be called from within the activity defining the
     * {@link MapView}.
     * <p/>
     *
     * @param mapView Map-View to set.
     */
    public void loadSelectedBaseMap(MapView mapView) {
        AbstractSpatialTable selectedSpatialTable = getSelectedBaseMapTable();
        if (selectedSpatialTable != null) {
            int selectedSpatialDataTypeCode = SpatialDataType.getCode4Name(selectedTileSourceType);
            MapGenerator selectedMapGenerator = null;
            try {
//                minZoom = 0;
//                maxZoom = 18;
//                defaultZoom = 17;
//                bounds_west = -180.0;
//                bounds_south = -85.05113;
//                bounds_east = 180.0;
//                bounds_north = 85.05113;
//                centerX = 0.0;
//                centerY = 0.0;
                SpatialDataType selectedSpatialDataType = SpatialDataType.getType4Code(selectedSpatialDataTypeCode);
                switch (selectedSpatialDataType) {
                    case MAP: {
                        MapTable selectedMapTable = (MapTable) selectedSpatialTable;
//                            minZoom = selectedMapTable.getMinZoom();
//                            maxZoom = selectedMapTable.getMaxZoom();
//                            defaultZoom = selectedMapTable.getDefaultZoom();
//                            bounds_west = selectedMapTable.getMinLongitude();
//                            bounds_east = selectedMapTable.getMaxLongitude();
//                            bounds_south = selectedMapTable.getMinLatitude();
//                            bounds_north = selectedMapTable.getMaxLatitude();
//                            centerX = selectedMapTable.getCenterX();
//                            centerY = selectedMapTable.getCenterY();
                        clearTileCache(mapView);
                        mapView.setMapFile(selectedMapTable.getDatabaseFile());
                        if (selectedMapTable.getXmlFile().exists()) {
                            try {
                                mapView.setRenderTheme(selectedMapTable.getXmlFile());
                            } catch (java.lang.Exception e) {
                                // ignore the theme
                                GPLog.error(this, "ERROR", e);
                            }
                        }
                        selectedMapGenerator = mapView.getMapGenerator();
                    }
                    break;
                    case MBTILES:
                    case GPKG:
                    case RASTERLITE2:
                    case SQLITE: {
                        SpatialRasterTable selectedSpatialRasterTable = (SpatialRasterTable) selectedSpatialTable;
//                            minZoom = selectedSpatialRasterTable.getMinZoom();
//                            maxZoom = selectedSpatialRasterTable.getMaxZoom();
//                            defaultZoom = selectedSpatialRasterTable.getDefaultZoom();
//                            bounds_west = selectedSpatialRasterTable.getMinLongitude();
//                            bounds_east = selectedSpatialRasterTable.getMaxLongitude();
//                            bounds_south = selectedSpatialRasterTable.getMinLatitude();
//                            bounds_north = selectedSpatialRasterTable.getMaxLatitude();
//                            centerX = selectedSpatialRasterTable.getCenterX();
//                            centerY = selectedSpatialRasterTable.getCenterY();
                        selectedMapGenerator = new GeopackageTileDownloader(selectedSpatialRasterTable);
                        clearTileCache(mapView);
                        mapView.setMapGenerator(selectedMapGenerator);
                    }
                    break;
                    case MAPURL: {
                        CustomTileTable selectedCustomTilesTable = (CustomTileTable) selectedSpatialTable;
                        CustomTileDatabaseHandler customTileDatabaseHandler = CustomTileDatabasesManager.getInstance()
                                .getCustomTileDatabaseHandler(selectedCustomTilesTable);
//                            minZoom = selectedCustomTilesTable.getMinZoom();
//                            maxZoom = selectedCustomTilesTable.getMaxZoom();
//                            defaultZoom = selectedCustomTilesTable.getDefaultZoom();
//                            bounds_west = selectedCustomTilesTable.getMinLongitude();
//                            bounds_east = selectedCustomTilesTable.getMaxLongitude();
//                            bounds_south = selectedCustomTilesTable.getMinLatitude();
//                            bounds_north = selectedCustomTilesTable.getMaxLatitude();
//                            centerX = selectedCustomTilesTable.getCenterX();
//                            centerY = selectedCustomTilesTable.getCenterY();
                        selectedMapGenerator = customTileDatabaseHandler.getCustomTileDownloader();
                        try {
                            clearTileCache(mapView);
                            mapView.setMapGenerator(selectedMapGenerator);
                            if (GPLog.LOG_HEAVY)
                                GPLog.addLogEntry(this, "MapsDirManager -I-> MAPURL setMapGenerator[" + selectedTileSourceType
                                        + "] selected_map[" + selectedTableDatabasePath + "]");
                        } catch (java.lang.NullPointerException e_mapurl) {
                            GPLog.error(this, "MapsDirManager setMapGenerator[" + selectedTileSourceType + "] selected_map["
                                    + selectedTableDatabasePath + "]", e_mapurl);
                        }
                    }
                    break;
                    default:
                        break;
                }
            } catch (jsqlite.Exception e) {
                selectedMapGenerator = MapGeneratorInternal.createMapGenerator(MapGeneratorInternal.mapnik);
                mapView.setMapGenerator(selectedMapGenerator);
                GPLog.error(this, "ERROR", e);
            }
        }
    }

    /**
     * Clear MapView TileCache.
     *
     * @param mapView the {@link MapView}.
     */
    private static void clearTileCache(MapView mapView) {
        if (mapView != null) {
            mapView.getInMemoryTileCache().destroy();
            if (mapView.getFileSystemTileCache().isPersistent()) {
                mapView.getFileSystemTileCache().setPersistent(false);
            }
            mapView.getFileSystemTileCache().destroy();
        }
    }

}
