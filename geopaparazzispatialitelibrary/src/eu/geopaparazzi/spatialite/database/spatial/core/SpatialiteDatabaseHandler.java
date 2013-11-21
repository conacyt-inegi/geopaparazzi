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
package eu.geopaparazzi.spatialite.database.spatial.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import jsqlite.Database;
import jsqlite.Exception;
import jsqlite.Stmt;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;

import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.util.ColorUtilities;

/**
 * An utility class to handle the spatial database.
 *
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class SpatialiteDatabaseHandler implements ISpatialDatabaseHandler {

    // 3857
    // private GeometryFactory gf = new GeometryFactory();
    // private WKBWriter wr = new WKBWriter();
    // private WKBReader wkbReader = new WKBReader(gf);

    private static final String METADATA_TABLE_GEOPACKAGE_CONTENTS = "geopackage_contents";
    private static final String METADATA_TABLE_TILE_MATRIX = "tile_matrix_metadata";
    private static final String METADATA_TABLE_RASTER_COLUMNS = "raster_columns";
    private static final String METADATA_TABLE_GEOMETRY_COLUMNS = "geometry_columns";

    private static final String METADATA_GEOPACKAGECONTENT_TABLE_NAME = "table_name";
    private static final String METADATA_GEOPACKAGECONTENT_DATA_TYPE = "data_type";
    // private static final String METADATA_GEOPACKAGECONTENT_DATA_TYPE_TILES = "tiles";
    private static final String METADATA_GEOPACKAGECONTENT_DATA_TYPE_FEATURES = "features";
    private static final String METADATA_TILE_TABLE_NAME = "t_table_name";
    private static final String METADATA_ZOOM_LEVEL = "zoom_level";
    private static final String METADATA_RASTER_COLUMN = "r_raster_column";
    private static final String METADATA_RASTER_TABLE_NAME = "r_table_name";
    private static final String METADATA_SRID = "srid";
    private static final String METADATA_GEOMETRY_TYPE4 = "geometry_type";
    private static final String METADATA_GEOMETRY_TYPE3 = "type";
    private static final String METADATA_GEOMETRY_COLUMN = "f_geometry_column";
    private static final String METADATA_TABLE_NAME = "f_table_name";
    // https://www.gaia-gis.it/fossil/libspatialite/wiki?name=metadata-4.0
    private static final String METADATA_VECTOR_LAYERS_TABLE_NAME = " vector_layers";
    private static final String METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME = " vector_layers_statistics";
    // vector_layers
    // SELECT layer_type,table_name,geometry_column,geometry_type,coord_dimension,srid,spatial_index_enabled FROM vector_layers
    // SELECT  * FROM vector_layers_statistics
    // SELECT vector_layers_statistics.layer_type,vector_layers_statistics.table_name,vector_layers_statistics.geometry_column,vector_layers_statistics.row_count,vector_layers_statistics.extent_min_x,vector_layers_statistics.extent_min_y,vector_layers_statistics.extent_max_x,vector_layers_statistics.extent_max_y,vector_layers.geometry_type,vector_layers.coord_dimension,vector_layers.srid,vector_layers.spatial_index_enabled,vector_layers_statistics.last_verified FROM vector_layers_statistics,vector_layers WHERE ((vector_layers_statistics.table_name = vector_layers.table_name) AND (vector_layers_statistics.geometry_column = vector_layers.geometry_column))
    // v4: SELECT f_table_name,f_geometry_column,geometry_type,srid FROM geometry_columns

    private static final String NAME = "name";
    private static final String SIZE = "size";
    private static final String FILLCOLOR = "fillcolor";
    private static final String STROKECOLOR = "strokecolor";
    private static final String FILLALPHA = "fillalpha";
    private static final String STROKEALPHA = "strokealpha";
    private static final String SHAPE = "shape";
    private static final String WIDTH = "width";
    private static final String TEXTSIZE = "textsize";
    private static final String TEXTFIELD = "textfield";
    private static final String ENABLED = "enabled";
    private static final String ORDER = "layerorder";
    private static final String DECIMATION = "decimationfactor";

    private final String PROPERTIESTABLE = "dataproperties";

    private Database db;

    private HashMap<String, Paint> fillPaints = new HashMap<String, Paint>();
    private HashMap<String, Paint> strokePaints = new HashMap<String, Paint>();

    private List<SpatialVectorTable> vectorTableList;
    private List<SpatialRasterTable> rasterTableList;
    private File file_map; // all DatabaseHandler/Table classes should use these names
    private String s_map_file; // [with path] all DatabaseHandler/Table classes should use these names
    private String s_name_file; // [without path] all DatabaseHandler/Table classes should use these names
    private String s_name; // all DatabaseHandler/Table classes should use these names
    private String s_description; // all DatabaseHandler/Table classes should use these names
    private String s_map_type; // all DatabaseHandler/Table classes should use these names
    private int minZoom;
    private int maxZoom;
    private double centerX; // wsg84
    private double centerY; // wsg84
    private double bounds_west; // wsg84
    private double bounds_east; // wsg84
    private double bounds_north; // wsg84
    private double bounds_south; // wsg84
    private int defaultZoom;
    public SpatialiteDatabaseHandler( String dbPath ) {
        try {
            file_map = new File(dbPath);
            if (!file_map.getParentFile().exists()) {
                throw new RuntimeException();
            }
            s_map_file=file_map.getAbsolutePath();
            s_name_file=file_map.getName();
            s_name = file_map.getName().substring(0, file_map.getName().lastIndexOf("."));
            db = new jsqlite.Database();
            db.open(s_map_file, jsqlite.Constants.SQLITE_OPEN_READWRITE
                    | jsqlite.Constants.SQLITE_OPEN_CREATE);
        } catch (Exception e) {
            GPLog.androidLog(4,"SpatialiteDatabaseHandler[" + file_map.getAbsolutePath() + "]", e);
        }
        this.minZoom = 0;
        this.maxZoom = 22;
        this.defaultZoom = minZoom;
        this.centerX = 0.0;
        this.centerY = 0.0;
        this.bounds_west = -180.0;
        this.bounds_south = -85.05113;
        this.bounds_east = 180.0;
        this.bounds_north = 85.05113;
        setDescription(s_name);
        // GPLog.androidLog(-1,"SpatialiteDatabaseHandler[" + file_map.getAbsolutePath() + "] name["+s_name+"] s_description["+s_description+"]");
    }
    // -----------------------------------------------
    /**
      * Return long name of map/file
      *
      * <p>default: file name with path and extention
      * <p>mbtiles : will be a '.mbtiles' sqlite-file-name
      * <p>map : will be a mapforge '.map' file-name
      *
      * @return file_map.getAbsolutePath();
      */
    public String getFileNamePath() {
        return this.s_map_file; // file_map.getAbsolutePath();
    }
    // -----------------------------------------------
    /**
      * Return short name of map/file
      *
      * <p>default: file name without path but with extention
      *
      * @return file_map.getAbsolutePath();
      */
    public String getFileName() {
        return this.s_name_file; // file_map.getName();
    }
    // -----------------------------------------------
    /**
      * Return short name of map/file
      *
      * <p>default: file name without path and extention
      * <p>mbtiles : metadata 'name'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_name as short name of map/file
      */
    public String getName() {
        if ((s_name == null) || (s_name.length() == 0))
        {
         s_name=this.file_map.getName().substring(0,this.file_map.getName().lastIndexOf("."));
        }
        return this.s_name; // comment or file-name without path and extention
    }
        // -----------------------------------------------
    /**
      * Return String of bounds [wms-format]
      *
      * <p>x_min,y_min,x_max,y_max
      *
      * @return bounds formatted using wms format
      */
    public String getBounds_toString() {
        return bounds_west+","+bounds_south+","+bounds_east+","+bounds_north;
    }
    // -----------------------------------------------
    /**
      * Return String of Map-Center with default Zoom
      *
      * <p>x_position,y_position,default_zoom
      *
      * @return center formatted using mbtiles format
      */
    public String getCenter_toString() {
        return centerX+","+centerY+","+defaultZoom;
    }
    // -----------------------------------------------
    /**
      * Return Min/Max Zoom as string
      *
      * <p>default :  1-22
      * <p>mbtiles : taken from value of metadata 'min/maxzoom'
      *
      * @return String min/maxzoom
      */
    public String getZoom_Levels() {
        return getMinZoom()+"-"+getMaxZoom();
    }
    // -----------------------------------------------
    /**
      * Return long description of map/file
      *
      * <p>default: s_name with bounds and center
      * <p>mbtiles : metadata description'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_description long description of map/file
      */
    public String getDescription() {
        if ((this.s_description == null) || (this.s_description.length() == 0) || (this.s_description.equals(this.s_name)))
         setDescription(getName()); // will set default values with bounds and center if it is the same as 's_name' or empty
        return this.s_description; // long comment
    }
     // -----------------------------------------------
    /**
      * Set long description of map/file
      *
      * <p>default: s_name with bounds and center
      * <p>mbtiles : metadata description'
      * <p>map : will be value of 'comment', if not null
      *
      * @return s_description long description of map/file
      */
    public void setDescription(String s_description) {
        if ((s_description == null) || (s_description.length() == 0) || (s_description.equals(this.s_name)))
        {
         this.s_description = getName()+" bounds["+getBounds_toString()+"] center["+getCenter_toString()+"]";
        }
        else
         this.s_description = s_description;
    }
    // -----------------------------------------------
    /**
      * Return map-file as 'File'
      *
      * <p>if the class does not fail, this file exists
      * <p>mbtiles : will be a '.mbtiles' sqlite-file
      * <p>map : will be a mapforge '.map' file
      *
      * @return file_map as File
      */
    public File getFile() {
        return this.file_map;
    }
    // -----------------------------------------------
    /**
      * Return Min Zoom
      *
      * <p>default :  0
      * <p>mbtiles : taken from value of metadata 'minzoom'
      * <p>map : value is given in 'StartZoomLevel'
      *
      * @return integer minzoom
      */
    public int getMinZoom() {
        return minZoom;
    }
    // -----------------------------------------------
    /**
      * Return Max Zoom
      *
      * <p>default :  22
      * <p>mbtiles : taken from value of metadata 'maxzoom'
      * <p>map : value not defined, seems to calculate bitmap from vector data [18]
      *
      * @return integer maxzoom
      */
    public int getMaxZoom() {
        return maxZoom;
    }
    // -----------------------------------------------
    /**
      * Return West X Value [Longitude]
      *
      * <p>default :  -180.0 [if not otherwise set]
      * <p>mbtiles : taken from 1st value of metadata 'bounds'
      *
      * @return double of West X Value [Longitude]
      */
    public double getMinLongitude() {
        return bounds_west;
    }
    // -----------------------------------------------
    /**
      * Return South Y Value [Latitude]
      *
      * <p>default :  -85.05113 [if not otherwise set]
      * <p>mbtiles : taken from 2nd value of metadata 'bounds'
      *
      * @return double of South Y Value [Latitude]
      */
    public double getMinLatitude() {
        return bounds_south;
    }
    // -----------------------------------------------
    /**
      * Return East X Value [Longitude]
      *
      * <p>default :  180.0 [if not otherwise set]
      * <p>mbtiles : taken from 3th value of metadata 'bounds'
      *
      * @return double of East X Value [Longitude]
      */
    public double getMaxLongitude() {
        return bounds_east;
    }
    // -----------------------------------------------
    /**
      * Return North Y Value [Latitude]
      *
      * <p>default :  85.05113 [if not otherwise set]
      * <p>mbtiles : taken from 4th value of metadata 'bounds'
      *
      * @return double of North Y Value [Latitude]
      */
    public double getMaxLatitude() {
        return bounds_north;
    }
    // -----------------------------------------------
    /**
      * Return Center X Value [Longitude]
      *
      * <p>default : center of bounds
      * <p>mbtiles : taken from 1st value of metadata 'center'
      *
      * @return double of X Value [Longitude]
      */
    public double getCenterX() {
        return centerX;
    }
    // -----------------------------------------------
    /**
      * Return Center Y Value [Latitude]
      *
      * <p>default : center of bounds
      * <p>mbtiles : taken from 2nd value of metadata 'center'
      *
      * @return double of Y Value [Latitude]
      */
    public double getCenterY() {
        return centerY;
    }
    // -----------------------------------------------
    /**
      * Retrieve Zoom level
      *
      * <p>default : minZoom
      * <p>mbtiles : taken from 3rd value of metadata 'center'
      *
     * @return defaultZoom
      */
    public int getDefaultZoom() {
        return defaultZoom;
    }
    // -----------------------------------------------
    /**
      * Set default Zoom level
      *
      * <p>default : minZoom
      * <p>mbtiles : taken from 3rd value of metadata 'center'
      *
      * @param i_zoom desired Zoom level
      */
    public void setDefaultZoom( int i_zoom ) {
        defaultZoom = i_zoom;
    }
    /**
     * Get the version of Spatialite.
     *
     * @return the version of Spatialite.
     * @throws Exception
     */
    public String getSpatialiteVersion() throws Exception {
        Stmt stmt = db.prepare("SELECT spatialite_version();");
        try {
            if (stmt.step()) {
                String value = stmt.column_string(0);
                return value;
            }
        } finally {
            stmt.close();
        }
        return "-";
    }

    /**
     * Get the version of proj.
     *
     * @return the version of proj.
     * @throws Exception
     */
    public String getProj4Version() throws Exception {
        Stmt stmt = db.prepare("SELECT proj4_version();");
        try {
            if (stmt.step()) {
                String value = stmt.column_string(0);
                return value;
            }
        } finally {
            stmt.close();
        }
        return "-";
    }

    /**
     * Get the version of geos.
     *
     * @return the version of geos.
     * @throws Exception
     */
    public String getGeosVersion() throws Exception {
        Stmt stmt = db.prepare("SELECT geos_version();");
        try {
            if (stmt.step()) {
                String value = stmt.column_string(0);
                return value;
            }
        } finally {
            stmt.close();
        }
        return "-";
    }

    @Override
    public List<SpatialVectorTable> getSpatialVectorTables( boolean forceRead ) throws Exception {
        if (vectorTableList == null || forceRead) {
            vectorTableList = new ArrayList<SpatialVectorTable>();
            StringBuilder sb_vector_layers = new StringBuilder();
            boolean is_vector_layer = true;
            boolean is3 = false;
            boolean is4 = false;
            // Take care that the fields are at the same position as the others
            // SELECT vector_layers_statistics.table_name,vector_layers_statistics.geometry_column,vector_layers.geometry_type,vector_layers.srid,vector_layers_statistics.layer_type,vector_layers_statistics.row_count,vector_layers_statistics.extent_min_x,vector_layers_statistics.extent_min_y,vector_layers_statistics.extent_max_x,vector_layers_statistics.extent_max_y,vector_layers.coord_dimension,vector_layers.spatial_index_enabled,vector_layers_statistics.last_verified FROM vector_layers_statistics,vector_layers WHERE ((vector_layers_statistics.table_name = vector_layers.table_name) AND (vector_layers_statistics.geometry_column = vector_layers.geometry_column))
            sb_vector_layers.append("SELECT ");
            sb_vector_layers.append(METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".table_name"); // 0
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".geometry_column"); // 1
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_TABLE_NAME+"."+METADATA_GEOMETRY_TYPE4); // 2
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_TABLE_NAME+"."+METADATA_SRID); // 3
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".layer_type"); // 4
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".row_count"); // 5
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".extent_min_x"); // 6
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".extent_min_y"); // 7
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".extent_max_x"); // 8
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".extent_max_y"); // 9
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_TABLE_NAME+".coord_dimension"); // 10
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_TABLE_NAME+".spatial_index_enabled"); // 11
            sb_vector_layers.append(", "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".last_verified"); // 12
            sb_vector_layers.append(" FROM "+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+","+METADATA_VECTOR_LAYERS_TABLE_NAME);
            sb_vector_layers.append(" WHERE(("+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".table_name="+METADATA_VECTOR_LAYERS_TABLE_NAME+".table_name) AND");
            sb_vector_layers.append(" ("+METADATA_VECTOR_LAYERS_STATISTICS_TABLE_NAME+".geometry_column="+METADATA_VECTOR_LAYERS_TABLE_NAME+".geometry_column))");
            String query_vector = sb_vector_layers.toString();
            Stmt stmt = null;
            try {
                stmt = db.prepare(query_vector);
            } catch (java.lang.Exception e_vector) {
            is_vector_layer = false;
            is3 = true;
            StringBuilder sb3 = new StringBuilder();
            sb3.append("select ");
            sb3.append(METADATA_TABLE_NAME);
            sb3.append(", ");
            sb3.append(METADATA_GEOMETRY_COLUMN);
            sb3.append(", ");
            sb3.append(METADATA_GEOMETRY_TYPE3);
            sb3.append(",");
            sb3.append(METADATA_SRID);
            sb3.append(" from ");
            sb3.append(METADATA_TABLE_GEOMETRY_COLUMNS);
            sb3.append(";");
            String query3 = sb3.toString();
            try {
                stmt = db.prepare(query3);
            } catch (java.lang.Exception e) {
             is3 = false;
             is4 = true;
                // try with spatialite 4 syntax
                // SELECT f_table_name,f_geometry_column,geometry_type,srid FROM geometry_columns
                StringBuilder sb4 = new StringBuilder();
                sb4.append("select ");
                sb4.append(METADATA_TABLE_NAME);
                sb4.append(", ");
                sb4.append(METADATA_GEOMETRY_COLUMN);
                sb4.append(", ");
                sb4.append(METADATA_GEOMETRY_TYPE4);
                sb4.append(",");
                sb4.append(METADATA_SRID);
                sb4.append(" from ");
                sb4.append(METADATA_TABLE_GEOMETRY_COLUMNS);
                sb4.append(";");
                String query4 = sb4.toString();
                stmt = db.prepare(query4);
             }
            }
            try {
                while( stmt.step() ) {
                    String table_name = stmt.column_string(0);
                    String geometry_column = stmt.column_string(1);
                    int geometry_type = 0;
                    if (is3) {
                        String type = stmt.column_string(2);
                        geometry_type = GeometryType.forValue(type);
                    } else {
                        geometry_type = stmt.column_int(2);
                    }
                   double[] centerCoordinate = {0.0, 0.0};
                   double[] boundsCoordinates = {-180.0f, -85.05113f, 180.0f, 85.05113f};
                   String srid = String.valueOf(stmt.column_int(3));
                   String s_layer_type="geometry";
                    int i_row_count=0;
                    int i_coord_dimension=0;
                    int i_spatial_index_enabled=0;
                    String s_last_verified="";
                    if (is_vector_layer) {
                     srid=stmt.column_string(3);
                     int i_srid = Integer.parseInt(srid);
                     s_layer_type=stmt.column_string(4);
                     i_row_count=stmt.column_int(5);
                     boundsCoordinates[0]=stmt.column_double(6);
                     boundsCoordinates[1]=stmt.column_double(7);
                     boundsCoordinates[2]=stmt.column_double(8);
                     boundsCoordinates[3]=stmt.column_double(9);
                     i_coord_dimension=stmt.column_int(10);
                     i_spatial_index_enabled=stmt.column_int(11);
                     s_last_verified=stmt.column_string(12);
                     if ((!srid.equals("4326")) && (i_srid > 2))
                     { // GeoPackage: have 0 or 1: srid has NOT been properly set - should be 4326 is: (1,2,3) [Luciad_GeoPackage.gpkg] ; try 4326 [wsg84]
                      int i_parm=0;
                      getSpatialVector_4326(srid,centerCoordinate,boundsCoordinates,i_parm);
                     }
                     else
                     {
                      centerCoordinate[0] = boundsCoordinates[0] + (boundsCoordinates[2] - boundsCoordinates[0]) / 2;
                      centerCoordinate[1] = boundsCoordinates[1] + (boundsCoordinates[3] - boundsCoordinates[1]) / 2;
                     }
                    }
                    SpatialVectorTable table = new SpatialVectorTable(getFileNamePath(),table_name, geometry_column, geometry_type, srid,centerCoordinate,boundsCoordinates,
                     s_layer_type,i_row_count,i_coord_dimension,i_spatial_index_enabled,s_last_verified);
                    vectorTableList.add(table);
                     GPLog.androidLog(-1,"SpatialiteDatabaseHandler["+getFileNamePath()+"][" + table.getBounds_toString()+ "] ["+is_vector_layer+"]");
                }
            } finally {
                stmt.close();
            }

            // now read styles
            checkPropertiesTable();

            // assign the styles
            for( SpatialVectorTable spatialTable : vectorTableList ) {
                Style style4Table = getStyle4Table(spatialTable.getName());
                if (style4Table == null) {
                    spatialTable.makeDefaultStyle();
                } else {
                    spatialTable.setStyle(style4Table);
                }
            }
        }
        OrderComparator orderComparator = new OrderComparator();
        Collections.sort(vectorTableList, orderComparator);

        return vectorTableList;
    }
    /**
     * Extract the center coordinate of a raster tileset.
     *
     * @param tableName the raster table name.
     * @param centerCoordinate teh coordinate array to update with the extracted values.
     */
    private void getSpatialVector_4326( String srid, double[] centerCoordinate, double[] boundsCoordinates, int i_parm ) {
        String centerQuery ="";
        try {
            Stmt centerStmt = null;
            double bounds_west = boundsCoordinates[0];
            double bounds_south = boundsCoordinates[1];
            double bounds_east = boundsCoordinates[2];
            double bounds_north = boundsCoordinates[3];
            // srid=3068
            // 3460.411441 1208.430179 49230.152810 38747.958906
            // SELECT CastToXY(ST_Transform(MakePoint((3460.411441+(49230.152810-3460.411441)/2),(1208.430179+(38747.958906-1208.430179)/2),3068),4326)) AS Center
            // SELECT CastToXY(ST_Transform(MakePoint(3460.411441,1208.430179,3068),4326)) AS South_West
            // SELECT CastToXY(ST_Transform(MakePoint(49230.152810,38747.958906,3068),4326)) AS North_East
            try {
                WKBReader wkbReader = new WKBReader();
                StringBuilder centerBuilder = new StringBuilder();
                centerBuilder.append("SELECT ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                // centerBuilder.append("select AsText(ST_Transform(MakePoint(");
                centerBuilder.append("("+bounds_west+" + ("+bounds_east+" - "+bounds_west+")/2), ");
                centerBuilder.append("("+bounds_south+" + ("+bounds_north+" - "+bounds_south+")/2), ");
                centerBuilder.append(srid);
                centerBuilder.append("),4326))) AS Center,");
                centerBuilder.append("ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                centerBuilder.append(""+bounds_west+","+bounds_south+", ");
                centerBuilder.append(srid);
                centerBuilder.append("),4326))) AS South_West,");
                centerBuilder.append("ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                centerBuilder.append(""+bounds_south+","+bounds_north+", ");
                centerBuilder.append(srid);
                centerBuilder.append("),4326))) AS North_East ");
                if (i_parm == 0) {
                } else {
                }
                //centerBuilder.append("';");
                centerQuery = centerBuilder.toString();

                centerStmt = db.prepare(centerQuery);
                if (centerStmt.step()) {
                    byte[] geomBytes = centerStmt.column_bytes(0);
                    Geometry geometry = wkbReader.read(geomBytes);
                    Coordinate coordinate = geometry.getCoordinate();
                    centerCoordinate[0] = coordinate.x;
                    centerCoordinate[1] = coordinate.y;
                    geomBytes = centerStmt.column_bytes(1);
                    geometry = wkbReader.read(geomBytes);
                    coordinate = geometry.getCoordinate();
                    boundsCoordinates[0] = coordinate.x;
                    boundsCoordinates[1] = coordinate.y;
                    geomBytes = centerStmt.column_bytes(2);
                    geometry = wkbReader.read(geomBytes);
                    coordinate = geometry.getCoordinate();
                    boundsCoordinates[2] = coordinate.x;
                    boundsCoordinates[3] = coordinate.y;
                }
            } finally {
                if (centerStmt != null)
                    centerStmt.close();
            }
        } catch (java.lang.Exception e) {
            GPLog.androidLog(4,"SpatialiteDatabaseHandler[" + file_map.getAbsolutePath() + "] sql["+centerQuery+"]", e);
        }
    }
    @Override
    public List<SpatialRasterTable> getSpatialRasterTables( boolean forceRead ) throws Exception {
        if (rasterTableList == null || forceRead) {
            rasterTableList = new ArrayList<SpatialRasterTable>();
            SpatialRasterTable table=null;
            StringBuilder sb = new StringBuilder();
            sb.append("select ");
            sb.append(METADATA_RASTER_TABLE_NAME);
            sb.append(", ");
            sb.append(METADATA_RASTER_COLUMN);
            sb.append(", srid from ");
            sb.append(METADATA_TABLE_RASTER_COLUMNS);
            sb.append(";");
            String query = sb.toString();
            Stmt stmt = db.prepare(query);
            try {
                while( stmt.step() ) {
                    String tableName = stmt.column_string(0);
                    String columnName = stmt.column_string(1);
                    String srid = String.valueOf(stmt.column_int(2));
                    // 20131107 mj10777: sometimes a table is being added more than one
                    if ((tableName != null) && (table == null)){
                        int[] zoomLevels = {0, 18};
                        getZoomLevels(tableName, zoomLevels);

                        double[] centerCoordinate = {0.0, 0.0};
                        double[] boundsCoordinates = {-180.0f, -85.05113f, 180.0f, 85.05113f};
                        int i_parm = 0; // tiles
                        i_parm = 1; // features
                        getCenterCoordinate4326(tableName, centerCoordinate, boundsCoordinates, i_parm);
                        // select r_table_name,r_raster_column,srid from raster_columns
                        // fromosm_tiles tile_data 3857
                        // GPLog.androidLog(-1,"getSpatialRasterTables: Geopackage["+getFileNamePath()+"] ");
                        table = new SpatialRasterTable(getFileNamePath(), "", srid, zoomLevels[0],
                                zoomLevels[1], centerCoordinate[0], centerCoordinate[1], null, boundsCoordinates);
                        table.setMapType("gpkg");
                        table.setTableName(tableName);
                        table.setColumnName(columnName);
                        this.minZoom = table.getMinZoom();
                        this.maxZoom = table.getMaxZoom();
                        this.defaultZoom = table.getDefaultZoom();
                        this.centerX = table.getCenterX();
                        this.centerY = table.getCenterY();
                        this.bounds_west = table.getMinLongitude();
                        this.bounds_south = table.getMinLatitude();
                        this.bounds_east = table.getMaxLongitude();
                        this.bounds_north = getMaxLatitude();
                        setDescription(columnName);
                        table.setDescription(this.s_description);
                        rasterTableList.add(table);
                    }

                }
            } finally {
                stmt.close();
            }
        }
        // OrderComparator orderComparator = new OrderComparator();
        // Collections.sort(rasterTableList, orderComparator);

        return rasterTableList;
    }

    /**
     * Extract the center coordinate of a raster tileset.
     *
     * @param tableName the raster table name.
     * @param centerCoordinate teh coordinate array to update with the extracted values.
     */
    private void getCenterCoordinate4326( String tableName, double[] centerCoordinate, double[] boundsCoordinates, int i_parm ) {
        try {
            Stmt centerStmt = null;
            try {
                WKBReader wkbReader = new WKBReader();
                // select ST_AsBinary(CastToXY(ST_Transform(MakePoint((min_x + (max_x-min_x)/2),
                // (min_y + (max_y-min_y)/2), srid), 4326))) from geopackage_contents where
                // table_name = "fromosm_tiles";
                StringBuilder centerBuilder = new StringBuilder();
                centerBuilder.append("select ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                // centerBuilder.append("select AsText(ST_Transform(MakePoint(");
                centerBuilder.append("(min_x + (max_x-min_x)/2), ");
                centerBuilder.append("(min_y + (max_y-min_y)/2), ");
                centerBuilder.append(METADATA_SRID);
                centerBuilder.append("),4326))) AS Center,");
                centerBuilder.append("ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                centerBuilder.append("min_x,min_y, ");
                centerBuilder.append(METADATA_SRID);
                centerBuilder.append("),4326))) AS South_West,");
                centerBuilder.append("ST_AsBinary(CastToXY(ST_Transform(MakePoint(");
                centerBuilder.append("max_x,max_y, ");
                centerBuilder.append(METADATA_SRID);
                centerBuilder.append("),4326))) AS North_East from ");
                centerBuilder.append(METADATA_TABLE_GEOPACKAGE_CONTENTS);
                centerBuilder.append(" where ");
                // i_parm = 0 : could be area of the whole world [tiles]
                // i_parm = 1 : could be area of main intrest [geonames] - assuming this is always
                // true : use as a 'center'-point to move to if out of area
                if (i_parm == 0) {
                    // select CastToXY(ST_Transform(MakePoint((min_x + (max_x-min_x)/2), (min_y +
                    // (max_y-min_y)/2), srid),4326)) AS
                    // Center,CastToXY(ST_Transform(MakePoint(min_x,min_y, srid),4326)) AS
                    // South_West,CastToXY(ST_Transform(MakePoint(max_x,max_y, srid), 4326)) AS
                    // North_East from geopackage_contents where table_name = "fromosm_tiles";
                    // Center: SRID=4326;POINT(0 0)
                    // South-West: SRID=4326;POINT(-179.9999999999996 -85.05110000000002)
                    // Norht_East: SRID=4326;POINT(179.9999999999996 85.05110000000002)
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_TABLE_NAME);
                    centerBuilder.append("='");
                    centerBuilder.append(tableName);
                } else {
                    // select CastToXY(ST_Transform(MakePoint((min_x + (max_x-min_x)/2), (min_y +
                    // (max_y-min_y)/2), srid),4326)) AS
                    // Center,CastToXY(ST_Transform(MakePoint(min_x,min_y, srid),4326)) AS
                    // South_West,CastToXY(ST_Transform(MakePoint(max_x,max_y, srid), 4326)) AS
                    // North_East from geopackage_contents where data_type = "features";
                    // Center: SRID=4326;POINT(-73.28333499999999 19.041665)
                    // South-West: SRID=4326;POINT(-75.5 18)
                    // Norht_East: SRID=4326;POINT(-71.06667 20.08333)
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_DATA_TYPE);
                    centerBuilder.append("='");
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_DATA_TYPE_FEATURES);
                }
                centerBuilder.append("';");
                String centerQuery = centerBuilder.toString();
                centerStmt = db.prepare(centerQuery);
                byte[] geomBytes=null;
                if (centerStmt.step()) {
                    // srid has been properly set [Sample_Geopackage_Haiti.gpkg]
                    geomBytes = centerStmt.column_bytes(0);
                    if (geomBytes == null)
                    { // srid has NOT been properly set - should be 4326 is: (1,2,3) [Luciad_GeoPackage.gpkg] ; try 4326 [wsg84]
                     centerBuilder = new StringBuilder();
                     centerBuilder.append("select ST_AsBinary(CastToXY(MakePoint(");
                     // centerBuilder.append("select AsText(ST_Transform(MakePoint(");
                     centerBuilder.append("(min_x + (max_x-min_x)/2), ");
                     centerBuilder.append("(min_y + (max_y-min_y)/2),");
                     centerBuilder.append("4326))) AS Center,");
                     centerBuilder.append("ST_AsBinary(CastToXY(MakePoint(");
                     centerBuilder.append("min_x,min_y,");
                     centerBuilder.append("4326))) AS South_West,");
                     centerBuilder.append("ST_AsBinary(CastToXY(MakePoint(");
                     centerBuilder.append("max_x,max_y,");
                     centerBuilder.append("4326))) AS North_East from ");
                     centerBuilder.append(METADATA_TABLE_GEOPACKAGE_CONTENTS);
                     centerBuilder.append(" where ");
                     // i_parm = 0 : could be area of the whole world [tiles]
                     // i_parm = 1 : could be area of main intrest [geonames] - assuming this is always
                    // true : use as a 'center'-point to move to if out of area
                    if (i_parm == 0) {
                    // select CastToXY(MakePoint((min_x + (max_x-min_x)/2), (min_y +
                    // (max_y-min_y)/2),4326)) AS
                    // Center,CastToXY(MakePoint(min_x,min_y),4326)) AS
                    // South_West,CastToXY(MakePoint(max_x,max_y, 4326)) AS
                    // North_East from geopackage_contents where table_name = "fromosm_tiles";
                    // Center: SRID=4326;POINT(0 0)
                    // South-West: SRID=4326;POINT(-179.9999999999996 -85.05110000000002)
                    // Norht_East: SRID=4326;POINT(179.9999999999996 85.05110000000002)
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_TABLE_NAME);
                    centerBuilder.append("='");
                    centerBuilder.append(tableName);
                } else {
                    // select CastToXY(ST_Transform(MakePoint((min_x + (max_x-min_x)/2), (min_y +
                    // (max_y-min_y)/2), srid),4326)) AS
                    // Center,CastToXY(ST_Transform(MakePoint(min_x,min_y, srid),4326)) AS
                    // South_West,CastToXY(ST_Transform(MakePoint(max_x,max_y, srid), 4326)) AS
                    // North_East from geopackage_contents where data_type = "features";
                    // Center: SRID=4326;POINT(-73.28333499999999 19.041665)
                    // South-West: SRID=4326;POINT(-75.5 18)
                    // Norht_East: SRID=4326;POINT(-71.06667 20.08333)
                    // select CastToXY(MakePoint((min_x + (max_x-min_x)/2), (min_y + (max_y-min_y)/2),4326)) AS Center,
                    //           CastToXY(MakePoint(min_x,min_y,4326)) AS South_West,
                    //           CastToXY(MakePoint(max_x,max_y,4326)) AS North_East from geopackage_contents where data_type='features';
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_DATA_TYPE);
                    centerBuilder.append("='");
                    centerBuilder.append(METADATA_GEOPACKAGECONTENT_DATA_TYPE_FEATURES);
                     centerBuilder.append("';");
                    }
                     centerQuery = centerBuilder.toString();
                     centerStmt = db.prepare(centerQuery);
                     if (centerStmt.step()) {
                      geomBytes = centerStmt.column_bytes(0);
                      if (geomBytes == null)
                      {
                       GPLog.androidLog(4,"SpatialiteDatabaseHandler["+getFileNamePath()+"]: getCenterCoordinate4326: sql["+centerQuery+"] ");
                      }
                     }
                    }
                }
                if (geomBytes != null)
                { // a result has been returned
                 Geometry geometry = wkbReader.read(geomBytes);
                 Coordinate coordinate = geometry.getCoordinate();
                 centerCoordinate[0] = coordinate.x;
                 centerCoordinate[1] = coordinate.y;
                 geomBytes = centerStmt.column_bytes(1);
                 geometry = wkbReader.read(geomBytes);
                 coordinate = geometry.getCoordinate();
                 boundsCoordinates[0] = coordinate.x;
                 boundsCoordinates[1] = coordinate.y;
                 geomBytes = centerStmt.column_bytes(2);
                 geometry = wkbReader.read(geomBytes);
                 coordinate = geometry.getCoordinate();
                 boundsCoordinates[2] = coordinate.x;
                 boundsCoordinates[3] = coordinate.y;
               }
            } finally {
                if (centerStmt != null)
                    centerStmt.close();
            }
        } catch (java.lang.Exception e) {
            GPLog.androidLog(4,"SpatialiteDatabaseHandler[" + file_map.getAbsolutePath() + "]", e);
        }
    }

    /**
     * Get the available zoomlevels for a raster table.
     *
     * @param tableName the raster table name.
     * @param zoomLevels the zoomlevels array to update with the min and max levels available.
     * @throws Exception
     */
    private void getZoomLevels( String tableName, int[] zoomLevels ) throws Exception {
        Stmt zoomStmt = null;
        try {
            StringBuilder zoomBuilder = new StringBuilder();
            zoomBuilder.append("SELECT min(");
            zoomBuilder.append(METADATA_ZOOM_LEVEL);
            zoomBuilder.append("),max(");
            zoomBuilder.append(METADATA_ZOOM_LEVEL);
            zoomBuilder.append(") FROM ");
            zoomBuilder.append(METADATA_TABLE_TILE_MATRIX);
            zoomBuilder.append(" WHERE ");
            zoomBuilder.append(METADATA_TILE_TABLE_NAME);
            zoomBuilder.append("='");
            zoomBuilder.append(tableName);
            zoomBuilder.append("';");
            String zoomQuery = zoomBuilder.toString();
            zoomStmt = db.prepare(zoomQuery);
            if (zoomStmt.step()) {
                zoomLevels[0] = zoomStmt.column_int(0);
                zoomLevels[1] = zoomStmt.column_int(1);
            }
        } finally {
            if (zoomStmt != null)
                zoomStmt.close();
        }
    }

    /**
     * Check availability of style for the tables.
     *
     * @throws Exception
     */
    private void checkPropertiesTable() throws Exception {
        String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='" + PROPERTIESTABLE + "';";
        Stmt stmt = db.prepare(checkTableQuery);
        boolean tableExists = false;
        try {
            if (stmt.step()) {
                String name = stmt.column_string(0);
                if (name != null) {
                    tableExists = true;
                }
            }
        } finally {
            stmt.close();
        }
        if (!tableExists) {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ");
            sb.append(PROPERTIESTABLE);
            sb.append(" (");
            sb.append(NAME).append(" TEXT, ");
            sb.append(SIZE).append(" REAL, ");
            sb.append(FILLCOLOR).append(" TEXT, ");
            sb.append(STROKECOLOR).append(" TEXT, ");
            sb.append(FILLALPHA).append(" REAL, ");
            sb.append(STROKEALPHA).append(" REAL, ");
            sb.append(SHAPE).append(" TEXT, ");
            sb.append(WIDTH).append(" REAL, ");
            sb.append(TEXTSIZE).append(" REAL, ");
            sb.append(TEXTFIELD).append(" TEXT, ");
            sb.append(ENABLED).append(" INTEGER, ");
            sb.append(ORDER).append(" INTEGER,");
            sb.append(DECIMATION).append(" REAL");
            sb.append(" );");
            String query = sb.toString();
            db.exec(query, null);

            for( SpatialVectorTable spatialTable : vectorTableList ) {
                StringBuilder sbIn = new StringBuilder();
                sbIn.append("insert into ").append(PROPERTIESTABLE);
                sbIn.append(" ( ");
                sbIn.append(NAME).append(" , ");
                sbIn.append(SIZE).append(" , ");
                sbIn.append(FILLCOLOR).append(" , ");
                sbIn.append(STROKECOLOR).append(" , ");
                sbIn.append(FILLALPHA).append(" , ");
                sbIn.append(STROKEALPHA).append(" , ");
                sbIn.append(SHAPE).append(" , ");
                sbIn.append(WIDTH).append(" , ");
                sbIn.append(TEXTSIZE).append(" , ");
                sbIn.append(TEXTFIELD).append(" , ");
                sbIn.append(ENABLED).append(" , ");
                sbIn.append(ORDER).append(" , ");
                sbIn.append(DECIMATION);
                sbIn.append(" ) ");
                sbIn.append(" values ");
                sbIn.append(" ( ");
                Style style = new Style();
                style.name = spatialTable.getName();
                sbIn.append(style.insertValuesString());
                sbIn.append(" );");

                String insertQuery = sbIn.toString();
                db.exec(insertQuery, null);
            }
        }
    }

    /**
     * Retrieve the {@link Style} for a given table.
     *
     * @param tableName
     * @return
     * @throws Exception
     */
    public Style getStyle4Table( String tableName ) throws Exception {
        Style style = new Style();
        style.name = tableName;

        StringBuilder sbSel = new StringBuilder();
        sbSel.append("select ");
        sbSel.append(SIZE).append(" , ");
        sbSel.append(FILLCOLOR).append(" , ");
        sbSel.append(STROKECOLOR).append(" , ");
        sbSel.append(FILLALPHA).append(" , ");
        sbSel.append(STROKEALPHA).append(" , ");
        sbSel.append(SHAPE).append(" , ");
        sbSel.append(WIDTH).append(" , ");
        sbSel.append(TEXTSIZE).append(" , ");
        sbSel.append(TEXTFIELD).append(" , ");
        sbSel.append(ENABLED).append(" , ");
        sbSel.append(ORDER).append(" , ");
        sbSel.append(DECIMATION);
        sbSel.append(" from ");
        sbSel.append(PROPERTIESTABLE);
        sbSel.append(" where ");
        sbSel.append(NAME).append(" ='").append(tableName).append("';");

        String selectQuery = sbSel.toString();
        Stmt stmt = db.prepare(selectQuery);
        try {
            if (stmt.step()) {
                style.size = (float) stmt.column_double(0);
                style.fillcolor = stmt.column_string(1);
                style.strokecolor = stmt.column_string(2);
                style.fillalpha = (float) stmt.column_double(3);
                style.strokealpha = (float) stmt.column_double(4);
                style.shape = stmt.column_string(5);
                style.width = (float) stmt.column_double(6);
                style.textsize = (float) stmt.column_double(7);
                style.textfield = stmt.column_string(8);
                style.enabled = stmt.column_int(9);
                style.order = stmt.column_int(10);
                style.decimationFactor = (float) stmt.column_double(11);
            }
        } finally {
            stmt.close();
        }
        return style;
    }

    public float[] getTableBounds( SpatialVectorTable spatialTable, String destSrid ) throws Exception {
        boolean doTransform = false;
        if (!spatialTable.getSrid().equals(destSrid)) {
            doTransform = true;
        }

        StringBuilder geomSb = new StringBuilder();
        if (doTransform)
            geomSb.append("ST_Transform(");
        geomSb.append(spatialTable.getGeomName());
        if (doTransform) {
            geomSb.append(", ");
            geomSb.append(destSrid);
            geomSb.append(")");
        }
        String geom = geomSb.toString();

        StringBuilder qSb = new StringBuilder();
        qSb.append("SELECT Min(MbrMinX(");
        qSb.append(geom);
        qSb.append(")) AS min_x, Min(MbrMinY(");
        qSb.append(geom);
        qSb.append(")) AS min_y,");
        qSb.append("Max(MbrMaxX(");
        qSb.append(geom);
        qSb.append(")) AS max_x, Max(MbrMaxY(");
        qSb.append(geom);
        qSb.append(")) AS max_y");
        qSb.append(" FROM ");
        qSb.append(spatialTable.getName());
        qSb.append(";");
         String selectQuery = qSb.toString();
          Stmt stmt = db.prepare(selectQuery);
        try {
            if (stmt.step()) {
                float w = (float) stmt.column_double(0);
                float s = (float) stmt.column_double(1);
                float e = (float) stmt.column_double(2);
                float n = (float) stmt.column_double(3);

                return new float[]{n, s, e, w};
            }
        } finally {
            stmt.close();
        }
        return null;
    }

    /**
     * Update a style definition.
     *
     * @param style the {@link Style} to set.
     * @throws Exception
     */
    public void updateStyle( Style style ) throws Exception {
        StringBuilder sbIn = new StringBuilder();
        sbIn.append("update ").append(PROPERTIESTABLE);
        sbIn.append(" set ");
        // sbIn.append(NAME).append("='").append(style.name).append("' , ");
        sbIn.append(SIZE).append("=").append(style.size).append(" , ");
        sbIn.append(FILLCOLOR).append("='").append(style.fillcolor).append("' , ");
        sbIn.append(STROKECOLOR).append("='").append(style.strokecolor).append("' , ");
        sbIn.append(FILLALPHA).append("=").append(style.fillalpha).append(" , ");
        sbIn.append(STROKEALPHA).append("=").append(style.strokealpha).append(" , ");
        sbIn.append(SHAPE).append("='").append(style.shape).append("' , ");
        sbIn.append(WIDTH).append("=").append(style.width).append(" , ");
        sbIn.append(TEXTSIZE).append("=").append(style.textsize).append(" , ");
        sbIn.append(TEXTFIELD).append("='").append(style.textfield).append("' , ");
        sbIn.append(ENABLED).append("=").append(style.enabled).append(" , ");
        sbIn.append(ORDER).append("=").append(style.order).append(" , ");
        sbIn.append(DECIMATION).append("=").append(style.decimationFactor);
        sbIn.append(" where ");
        sbIn.append(NAME);
        sbIn.append("='");
        sbIn.append(style.name);
        sbIn.append("';");

        String updateQuery = sbIn.toString();
        db.exec(updateQuery, null);
    }

    @Override
    public Paint getFillPaint4Style( Style style ) {
        Paint paint = fillPaints.get(style.name);
        if (paint == null) {
            paint = new Paint();
            fillPaints.put(style.name, paint);
        }
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtilities.toColor(style.fillcolor));
        float alpha = style.fillalpha * 255f;
        paint.setAlpha((int) alpha);
        return paint;
    }

    @Override
    public Paint getStrokePaint4Style( Style style ) {
        Paint paint = strokePaints.get(style.name);
        if (paint == null) {
            paint = new Paint();
            strokePaints.put(style.name, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setStrokeCap(Cap.ROUND);
        paint.setStrokeJoin(Join.ROUND);
        paint.setColor(ColorUtilities.toColor(style.strokecolor));
        float alpha = style.strokealpha * 255f;
        paint.setAlpha((int) alpha);
        paint.setStrokeWidth(style.width);
        return paint;
    }

    public List<byte[]> getWKBFromTableInBounds( String destSrid, SpatialVectorTable table, double n, double s, double e, double w ) {
        List<byte[]> list = new ArrayList<byte[]>();
        String query = buildGeometriesInBoundsQuery(destSrid, table, n, s, e, w);
        try {
            Stmt stmt = db.prepare(query);
            try {
                while( stmt.step() ) {
                    list.add(stmt.column_bytes(0));
                }
            } finally {
                stmt.close();
            }
            return list;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public byte[] getRasterTile( String query ) {
        try {
            Stmt stmt = db.prepare(query);
            try {
                if (stmt.step()) {
                    byte[] bytes = stmt.column_bytes(0);
                    return bytes;
                }
            } finally {
                stmt.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public GeometryIterator getGeometryIteratorInBounds( String destSrid, SpatialVectorTable table, double n, double s, double e,
            double w ) {
        String query = buildGeometriesInBoundsQuery(destSrid, table, n, s, e, w);
        return new GeometryIterator(db, query);
    }

    private String buildGeometriesInBoundsQuery( String destSrid, SpatialVectorTable table, double n, double s, double e, double w ) {
        boolean doTransform = false;
        if (!table.getSrid().equals(destSrid)) {
            doTransform = true;
        }

        StringBuilder mbrSb = new StringBuilder();
        if (doTransform)
            mbrSb.append("ST_Transform(");
        mbrSb.append("BuildMBR(");
        mbrSb.append(w);
        mbrSb.append(", ");
        mbrSb.append(n);
        mbrSb.append(", ");
        mbrSb.append(e);
        mbrSb.append(", ");
        mbrSb.append(s);
        if (doTransform) {
            mbrSb.append(", ");
            mbrSb.append(destSrid);
            mbrSb.append("), ");
            mbrSb.append(table.getSrid());
        }
        mbrSb.append(")");
        String mbr = mbrSb.toString();

        StringBuilder qSb = new StringBuilder();
        qSb.append("SELECT ST_AsBinary(CastToXY(");
        if (doTransform)
            qSb.append("ST_Transform(");
        qSb.append(table.getGeomName());
        if (doTransform) {
            qSb.append(", ");
            qSb.append(destSrid);
            qSb.append(")");
        }
        qSb.append("))");
        // qSb.append(", AsText(");
        // if (doTransform)
        // qSb.append("ST_Transform(");
        // qSb.append(table.geomName);
        // if (doTransform) {
        // qSb.append(", ");
        // qSb.append(destSrid);
        // qSb.append(")");
        // }
        // qSb.append(")");
        qSb.append(" FROM ");
        qSb.append(table.getName());
        qSb.append(" WHERE ST_Intersects(");
        qSb.append(table.getGeomName());
        qSb.append(", ");
        qSb.append(mbr);
        qSb.append(") = 1");
        qSb.append("   AND ROWID IN (");
        qSb.append("     SELECT ROWID FROM Spatialindex WHERE f_table_name ='");
        qSb.append(table.getName());
        qSb.append("'");
        qSb.append("     AND search_frame = ");
        qSb.append(mbr);
        qSb.append(" );");
        String q = qSb.toString();

        return q;
    }
    /**
     * Close  all Databases that may be open
     * <p>sqlite 'SpatialRasterTable,SpatialVectorTable and MBTilesDroidSpitter' databases will be closed with '.close();' if active
     */
    public void close() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    public void intersectionToStringBBOX( String boundsSrid, SpatialVectorTable spatialTable, double n, double s, double e,
            double w, StringBuilder sb, String indentStr ) throws Exception {
        boolean doTransform = false;
        if (!spatialTable.getSrid().equals(boundsSrid)) {
            doTransform = true;
        }

        String query = null;

        // SELECT che-cazzo-ti-pare-a-te
        // FROM qualche-tavola
        // WHERE ROWID IN (
        // SELECT ROWID
        // FROM SpatialIndex
        // WHERE f_table_name = 'qualche-tavola'
        // AND search_frame = il-tuo-bbox
        // );

        // {
        // StringBuilder sbQ = new StringBuilder();
        // sbQ.append("SELECT ");
        // sbQ.append("*");
        // sbQ.append(" from ").append(spatialTable.name);
        // sbQ.append(" where ROWID IN (");
        // sbQ.append(" SELECT ROWID FROM Spatialindex WHERE f_table_name ='");
        // sbQ.append(spatialTable.name);
        // sbQ.append("' AND search_frame = ");
        // if (doTransform)
        // sbQ.append("ST_Transform(");
        // sbQ.append("BuildMBR(");
        // sbQ.append(w);
        // sbQ.append(", ");
        // sbQ.append(s);
        // sbQ.append(", ");
        // sbQ.append(e);
        // sbQ.append(", ");
        // sbQ.append(n);
        // if (doTransform) {
        // sbQ.append(", ");
        // sbQ.append(boundsSrid);
        // }
        // sbQ.append(")");
        // if (doTransform) {
        // sbQ.append(",");
        // sbQ.append(spatialTable.srid);
        // sbQ.append(")");
        // }
        // sbQ.append(");");
        //
        // query = sbQ.toString();
        // Logger.i(this, query);
        // }
        {
            StringBuilder sbQ = new StringBuilder();
            sbQ.append("SELECT ");
            sbQ.append("*");
            sbQ.append(" from ").append(spatialTable.getName());
            sbQ.append(" where ST_Intersects(");
            if (doTransform)
                sbQ.append("ST_Transform(");
            sbQ.append("BuildMBR(");
            sbQ.append(w);
            sbQ.append(", ");
            sbQ.append(s);
            sbQ.append(", ");
            sbQ.append(e);
            sbQ.append(", ");
            sbQ.append(n);
            if (doTransform) {
                sbQ.append(", ");
                sbQ.append(boundsSrid);
                sbQ.append("),");
                sbQ.append(spatialTable.getSrid());
            }
            sbQ.append("),");
            sbQ.append(spatialTable.getGeomName());
            sbQ.append(");");

            query = sbQ.toString();

            // Logger.i(this, query);
        }

        Stmt stmt = db.prepare(query);
        try {
            while( stmt.step() ) {
                int column_count = stmt.column_count();
                for( int i = 0; i < column_count; i++ ) {
                    String cName = stmt.column_name(i);
                    if (cName.equalsIgnoreCase(spatialTable.getGeomName())) {
                        continue;
                    }

                    String value = stmt.column_string(i);
                    sb.append(indentStr).append(cName).append(": ").append(value).append("\n");
                }
                sb.append("\n");
            }
        } finally {
            stmt.close();
        }
    }

    public void intersectionToString4Polygon( String queryPointSrid, SpatialVectorTable spatialTable, double n, double e,
            StringBuilder sb, String indentStr ) throws Exception {
        boolean doTransform = false;
        if (!spatialTable.getSrid().equals(queryPointSrid)) {
            doTransform = true;
        }

        StringBuilder sbQ = new StringBuilder();
        sbQ.append("SELECT * FROM ");
        sbQ.append(spatialTable.getName());
        sbQ.append(" WHERE ST_Intersects(");
        sbQ.append(spatialTable.getGeomName());
        sbQ.append(", ");
        if (doTransform)
            sbQ.append("ST_Transform(");
        sbQ.append("MakePoint(");
        sbQ.append(e);
        sbQ.append(",");
        sbQ.append(n);
        if (doTransform) {
            sbQ.append(", ");
            sbQ.append(queryPointSrid);
            sbQ.append("), ");
            sbQ.append(spatialTable.getSrid());
        }
        sbQ.append(")) = 1 ");
        sbQ.append("AND ROWID IN (");
        sbQ.append("SELECT ROWID FROM Spatialindex WHERE f_table_name ='");
        sbQ.append(spatialTable.getName());
        sbQ.append("' AND search_frame = ");
        if (doTransform)
            sbQ.append("ST_Transform(");
        sbQ.append("MakePoint(");
        sbQ.append(e);
        sbQ.append(",");
        sbQ.append(n);
        if (doTransform) {
            sbQ.append(", ");
            sbQ.append(queryPointSrid);
            sbQ.append("), ");
            sbQ.append(spatialTable.getSrid());
        }
        sbQ.append("));");
        String query = sbQ.toString();

        Stmt stmt = db.prepare(query);
        try {
            while( stmt.step() ) {
                int column_count = stmt.column_count();
                for( int i = 0; i < column_count; i++ ) {
                    String cName = stmt.column_name(i);
                    if (cName.equalsIgnoreCase(spatialTable.getGeomName())) {
                        continue;
                    }

                    String value = stmt.column_string(i);
                    sb.append(indentStr).append(cName).append(": ").append(value).append("\n");
                }
                sb.append("\n");
            }
        } finally {
            stmt.close();
        }
    }

    // public String queryComuni() {
    // sb.append(SEP);
    // sb.append("Query Comuni...\n");
    //
    // String query = "SELECT " + NOME + //
    // " from " + COMUNITABLE + //
    // " order by " + NOME + ";";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // int index = 0;
    // while( stmt.step() ) {
    // String nomeStr = stmt.column_string(0);
    // sb.append("\t").append(nomeStr).append("\n");
    // if (index++ > 5) {
    // break;
    // }
    // }
    // sb.append("\t...");
    // stmt.close();
    // } catch (Exception e) {
    // error(e);
    // }
    //
    // sb.append("Done...\n");
    //
    // return sb.toString();
    // }
    //
    // public String queryComuniWithGeom() {
    // sb.append(SEP);
    // sb.append("Query Comuni with AsText(Geometry)...\n");
    //
    // String query = "SELECT " + NOME + //
    // " , " + AS_TEXT_GEOMETRY + //
    // " as geom from " + COMUNITABLE + //
    // " where geom not null;";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // while( stmt.step() ) {
    // String nomeStr = stmt.column_string(0);
    // String geomStr = stmt.column_string(1);
    // String substring = geomStr;
    // if (substring.length() > 40)
    // substring = geomStr.substring(0, 40);
    // sb.append("\t").append(nomeStr).append(" - ").append(substring).append("...\n");
    // break;
    // }
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // sb.append("Done...\n");
    //
    // return sb.toString();
    // }
    //
    // public String queryGeomTypeAndSrid() {
    // sb.append(SEP);
    // sb.append("Query Comuni geom type and srid...\n");
    //
    // String query = "SELECT " + NOME + //
    // " , " + AS_TEXT_GEOMETRY + //
    // " as geom from " + COMUNITABLE + //
    // " where geom not null;";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // while( stmt.step() ) {
    // String nomeStr = stmt.column_string(0);
    // String geomStr = stmt.column_string(1);
    // String substring = geomStr;
    // if (substring.length() > 40)
    // substring = geomStr.substring(0, 40);
    // sb.append("\t").append(nomeStr).append(" - ").append(substring).append("...\n");
    // break;
    // }
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // sb.append("Done...\n");
    //
    // return sb.toString();
    // }
    //
    // public String queryComuniArea() {
    // sb.append(SEP);
    // sb.append("Query Comuni area sum...\n");
    //
    // String query = "SELECT ST_Area(Geometry) / 1000000.0 from " + COMUNITABLE + //
    // ";";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // double totalArea = 0;
    // while( stmt.step() ) {
    // double area = stmt.column_double(0);
    // totalArea = totalArea + area;
    // }
    // sb.append("\tTotal area by summing each area: ").append(totalArea).append("Km2\n");
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // query = "SELECT sum(ST_Area(Geometry) / 1000000.0) from " + COMUNITABLE + //
    // ";";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // double totalArea = 0;
    // if (stmt.step()) {
    // double area = stmt.column_double(0);
    // totalArea = totalArea + area;
    // }
    // sb.append("\tTotal area by summing in query: ").append(totalArea).append("Km2\n");
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // sb.append("Done...\n");
    //
    // return sb.toString();
    // }
    //
    // public String queryComuniNearby() {
    // sb.append(SEP);
    // sb.append("Query Comuni nearby...\n");
    //
    // String query =
    // "SELECT Hex(ST_AsBinary(ST_Buffer(Geometry, 1.0))), ST_Srid(Geometry), ST_GeometryType(Geometry) from "
    // + COMUNITABLE + //
    // " where " + NOME + "= 'Bolzano';";
    // sb.append("Execute query: ").append(query).append("\n");
    // String bufferGeom = "";
    // String bufferGeomShort = "";
    // try {
    // Stmt stmt = db.prepare(query);
    // if (stmt.step()) {
    // bufferGeom = stmt.column_string(0);
    // String geomSrid = stmt.column_string(1);
    // String geomType = stmt.column_string(2);
    // sb.append("\tThe selected geometry is of type: ").append(geomType).append(" and of SRID: ").append(geomSrid)
    // .append("\n");
    // }
    // bufferGeomShort = bufferGeom;
    // if (bufferGeom.length() > 10)
    // bufferGeomShort = bufferGeom.substring(0, 10) + "...";
    // sb.append("\tBolzano polygon buffer geometry in HEX: ").append(bufferGeomShort).append("\n");
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    //
    // query = "SELECT " + NOME + ", AsText(ST_centroid(Geometry)) from " + COMUNITABLE + //
    // " where ST_Intersects( ST_GeomFromWKB(x'" + bufferGeom + "') , Geometry );";
    // // just for print
    // String tmpQuery = "SELECT " + NOME + " from " + COMUNITABLE + //
    // " where ST_Intersects( ST_GeomFromWKB(x'" + bufferGeomShort + "') , Geometry );";
    // sb.append("Execute query: ").append(tmpQuery).append("\n");
    // try {
    // sb.append("\tComuni nearby Bolzano: \n");
    // Stmt stmt = db.prepare(query);
    // while( stmt.step() ) {
    // String name = stmt.column_string(0);
    // String wkt = stmt.column_string(1);
    // sb.append("\t\t").append(name).append(" - with centroid in ").append(wkt).append("\n");
    // }
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // sb.append("Done...\n");
    //
    // return sb.toString();
    // }
    //
    // public byte[] getBolzanoWKB() {
    // String query = "SELECT ST_AsBinary(ST_Transform(Geometry, 4326)) from " + COMUNITABLE + //
    // " where " + NOME + "= 'Bolzano';";
    // try {
    // Stmt stmt = db.prepare(query);
    // byte[] theGeom = null;
    // if (stmt.step()) {
    // theGeom = stmt.column_bytes(0);
    // }
    // stmt.close();
    // return theGeom;
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // return null;
    // }
    //
    // public List<byte[]> getIntersectingWKB( double n, double s, double e, double w ) {
    // List<byte[]> list = new ArrayList<byte[]>();
    // Coordinate ll = new Coordinate(w, s);
    // Coordinate ul = new Coordinate(w, n);
    // Coordinate ur = new Coordinate(e, n);
    // Coordinate lr = new Coordinate(e, s);
    // Polygon bboxPolygon = gf.createPolygon(new Coordinate[]{ll, ul, ur, lr, ll});
    //
    // byte[] bbox = wr.write(bboxPolygon);
    // String query = "SELECT ST_AsBinary(ST_Transform(Geometry, 4326)) from " + COMUNITABLE + //
    // " where ST_Intersects(ST_Transform(Geometry, 4326), ST_GeomFromWKB(?));";
    // try {
    // Stmt stmt = db.prepare(query);
    // stmt.bind(1, bbox);
    // while( stmt.step() ) {
    // list.add(stmt.column_bytes(0));
    // }
    // stmt.close();
    // return list;
    // } catch (Exception ex) {
    // ex.printStackTrace();
    // }
    // return null;
    // }
    //
    // public String doSimpleTransform() {
    //
    // sb.append(SEP);
    // sb.append("Coordinate transformation...\n");
    //
    // String query = "SELECT AsText(Transform(MakePoint(" + TEST_LON + ", " + TEST_LAT +
    // ", 4326), 32632));";
    // sb.append("Execute query: ").append(query).append("\n");
    // try {
    // Stmt stmt = db.prepare(query);
    // if (stmt.step()) {
    // String pointStr = stmt.column_string(0);
    // sb.append("\t").append(TEST_LON + "/" + TEST_LAT + "/EPSG:4326").append(" = ")//
    // .append(pointStr + "/EPSG:32632").append("...\n");
    // }
    // stmt.close();
    // } catch (Exception e) {
    // e.printStackTrace();
    // sb.append(ERROR).append(e.getLocalizedMessage()).append("\n");
    // }
    // sb.append("Done...\n");
    //
    // return sb.toString();
    //
    // }

}
