package com.esri.arcgis.android.samples.geometryeditor;

import java.util.ArrayList;
import java.util.Random;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISFeatureLayer.SELECTION_METHOD;
import com.esri.core.geometry.Point;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.FeatureEditResult;
import com.esri.core.map.FeatureSet;
import com.esri.core.map.FeatureTemplate;
import com.esri.core.map.Graphic;
import com.esri.core.tasks.SpatialRelationship;
import com.esri.core.tasks.ags.query.Query;
import com.example.asm540gpscollector.R;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends Activity implements LocationListener, OnMarkerClickListener, OnMarkerDragListener, OnMapClickListener {
	public static GoogleMap map;
	private Location location;
	private LocationManager locationManager;
	LatLng currentLatLng = new LatLng(0,0);
	static double acc = 0.0;
	static int numSats = 0;
	String provider;
	ArcGISFeatureLayer featureLayer;
	String username = null;
	Menu myMenu;
	ArrayList<Marker> featureLayerPoints = new ArrayList<Marker>();
	public static SharedPreferences prefs;
	private boolean firstStart;
	boolean MARKER_SELECTED = false;
	boolean ADD_POINTS_MODE = false;
	Marker markerClicked = null;
	ArrayList<Point> fLPoints = new ArrayList<Point>();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.main);
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Criteria crit = new Criteria();
		provider = locationManager.getBestProvider(crit, false);
		location = locationManager.getLastKnownLocation(provider);
		locationManager.requestLocationUpdates(provider, 5000, 0, this);
		if (location != null) {
			currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
		}
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
		Intent gpsOptionsIntent = new Intent(  
			    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);  
			startActivity(gpsOptionsIntent);
		}
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		firstStart = prefs.getBoolean("first_start", true);
		if (firstStart){
			changeNameDialog();
			SharedPreferences.Editor edit = prefs.edit();
	        edit = prefs.edit();
	        edit.putBoolean("first_start", false);
	        edit.commit();
		}
		username = prefs.getString("username", getRandomUsername());
		MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
		map = mapFragment.getMap();
		map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
		map.setMyLocationEnabled(true);
		map.getUiSettings().setZoomControlsEnabled(false);
		map.setOnMarkerClickListener(this);
		map.setOnMarkerDragListener(this);
		map.setOnMapClickListener(this);
		
		featureLayer = new ArcGISFeatureLayer("http://buckeye.agriculture.purdue.edu:6080/ArcGIS/rest/services/GPSCollectorService/FeatureServer/0", ArcGISFeatureLayer.MODE.ONDEMAND);
		synchMarkers();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		myMenu = menu;
		// Make an action bar and don't display the app title
		ActionBar actionBar = getActionBar();
		actionBar.setTitle("ASM540 GPS Collector");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Choose DEM Menu Item
		if (item.getItemId() == R.id.menu_change_name) {
			changeNameDialog();
			return true;
		}  else if (item.getItemId() == R.id.menu_add_point) {
//			addPoint();
			if (ADD_POINTS_MODE) {
				ADD_POINTS_MODE = false;
				addPoint(currentLatLng);
			} else {
				ADD_POINTS_MODE = true;
			}
			return true;
		} else if (item.getItemId() == R.id.menu_delete) {
			if (MARKER_SELECTED) {
				deletePoint();
				MenuItem mi = myMenu.findItem(R.id.menu_add_point);
				mi.setVisible(true);
				mi = myMenu.findItem(R.id.menu_refresh_points);
				mi.setVisible(true);
			} else {
			clearAllPointsDialog();
			}
			return true;
		} else if (item.getItemId() == R.id.menu_refresh_points) {
			synchMarkers();
		}
		return true;
	}

	private void addPoint(final LatLng arg0) {
		featureLayerPoints.add(map.addMarker(new MarkerOptions()
		.position(arg0)
		.title(username)));
		FeatureTemplate template;
		template = featureLayer.getTemplates()[0];
		Graphic addGraphic = featureLayer.createFeatureWithTemplate(template, new Point(arg0.longitude, arg0.latitude));
		featureLayer.applyEdits(new Graphic[] { addGraphic }, null, null,
				new CallbackListener<FeatureEditResult[][]>() {

					@Override
					public void onError(Throwable e) {
					}

					public void onCallback(FeatureEditResult[][] objs) {
					}
				});
	}
	
	private void deletePoint() {
		Point removePoint = new Point(markerClicked.getPosition().longitude, markerClicked.getPosition().latitude);
		Query query = new Query();
		query.setGeometry(removePoint);
		query.setSpatialRelationship(SpatialRelationship.INTERSECTS);
		markerClicked.remove();
		featureLayer.selectFeatures(query, ArcGISFeatureLayer.SELECTION_METHOD.NEW, new CallbackListener<FeatureSet>() {

			// handle any errors
			public void onError(Throwable e) {
				Log.d("on error remove point", "Select Features Error" + e.getLocalizedMessage());
			}

			public void onCallback(FeatureSet queryResults) {
				if (queryResults.getGraphics().length > 0) {
					Log.d("oncallback remove point", "Feature found id="
							+ queryResults.getGraphics()[0].getAttributeValue(featureLayer.getObjectIdField()));
					featureLayer.applyEdits(null, new Graphic[] {featureLayer.getSelectedFeatures()[0]}, null,
							new CallbackListener<FeatureEditResult[][]>() {

								@Override
								public void onError(Throwable e) {
									Log.d("onerror applyedits removept", e.getMessage());
								}

								public void onCallback(FeatureEditResult[][] objs) {
								}
							});
				}
			}
		});
		MARKER_SELECTED = false;
	}
	
	public void deleteAllPoints() {
		map.clear();
		Query query = new Query();
		query.setWhere("OBJECTID > -1");
		featureLayer.selectFeatures(query, SELECTION_METHOD.NEW, new CallbackListener<FeatureSet>() {

			public void onError(Throwable e) {
				Log.d("on error", "Select Features Error" + e.getLocalizedMessage());
			}

			public void onCallback(FeatureSet queryResults) {
				Graphic[] deleteGraphics = featureLayer.getSelectedFeatures();
				featureLayer.applyEdits(null, deleteGraphics, null,
						new CallbackListener<FeatureEditResult[][]>() {

							@Override
							public void onError(Throwable e) {
								Log.d("on error applyedits", e.getMessage());
							}

							public void onCallback(FeatureEditResult[][] objs) {
							}
						});
			}
		});
	}

	public boolean synchMarkers() {
		map.clear();
		featureLayerPoints.clear();
		fLPoints.clear();
		Query query = new Query();
		query.setWhere("OBJECTID > -1");
		featureLayer.selectFeatures(query, SELECTION_METHOD.NEW, new CallbackListener<FeatureSet>() {
			public void onError(Throwable e) {
				Log.d("onerror", "Select Features Error" + e.getLocalizedMessage());
			}

			public void onCallback(FeatureSet queryResults) {
				for (int i = 0; i < queryResults.getGraphics().length; i++) {
					Point pt = (Point) queryResults.getGraphics()[i].getGeometry();
					fLPoints.add(pt);
				}
				runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    	for (int i = 0; i < fLPoints.size(); i++) {
                			featureLayerPoints.add(map.addMarker(new MarkerOptions()
                				.position(new LatLng(fLPoints.get(i).getY(), fLPoints.get(i).getX()))
                				.title(username)));
                			Log.w("marker", featureLayerPoints.get(i).toString());
                		}
                    }
             });
			}
		});
		return true;
	}

	public void changeNameDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final View v = LayoutInflater.from(this).inflate(R.layout.change_name_dialog, null);
		if (username != null) {
			builder.setTitle("Current Username: " + username);
		}
		builder.setView(v);
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				if (username == null) {
					getRandomUsername();
					Toast.makeText(MainActivity.this,
							"You've been assigned a random username of " + username + ".  To change it, go to Menu > Change Name.", Toast.LENGTH_LONG)
							.show();
				}
				SharedPreferences.Editor edit = prefs.edit();
	            edit = prefs.edit();
	            edit.putString("username", username);
	            edit.commit();
			}
		});
		builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				username = ((EditText)v.findViewById(R.id.username)).getText().toString();
				SharedPreferences.Editor edit = prefs.edit();
	            edit = prefs.edit();
	            edit.putString("username", username);
	            edit.commit();
			}
		});
		builder.show();
	}
	
	public void clearAllPointsDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final View v = LayoutInflater.from(this).inflate(R.layout.clear_all_points_dialog, null);
		builder.setTitle("Are you sure you want to clear all points with USERNAME = " + username);
		builder.setView(v);
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
			}
		});
		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				deleteAllPoints();
			}
		});
		builder.show();
	}
	
	public String getRandomUsername() {
		Random generator = new Random();
		int r = generator.nextInt(Integer.MAX_VALUE) + 1;
		return username = "user" + Integer.toString(r);
	}

	public void onGpsStatusChanged(int event) {
		int Satellites = 0;
		int SatellitesInFix = 0;
		//	    int timetofix = locationManager.getGpsStatus(null).getTimeToFirstFix();
		for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
			if(sat.usedInFix()) {
				SatellitesInFix++;              
			}
			Satellites++;
		}
		numSats = SatellitesInFix;
		//	    Log.i(TAG, String.valueOf(Satellites) + " Used In Last Fix ("+SatellitesInFix+")"); 
	}

	public void onLocationChanged(Location l) {
		if (l != null) {
			currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
			acc = l.getAccuracy();
			provider = l.getProvider();
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public boolean onMarkerClick(Marker arg0) {
		MARKER_SELECTED = true;
		markerClicked = arg0;
		MenuItem mi = myMenu.findItem(R.id.menu_add_point);
		mi.setVisible(false);
		mi = myMenu.findItem(R.id.menu_refresh_points);
		mi.setVisible(false);
		return false;
	}

	@Override
	public void onMarkerDrag(Marker arg0) {
	}

	@Override
	public void onMarkerDragEnd(Marker arg0) {
//		featurelayer.applyEdits(null, null, [], callback)
	}

	@Override
	public void onMarkerDragStart(Marker arg0) {
	}

	@Override
	public void onMapClick(LatLng arg0) {
		if (MARKER_SELECTED == true) {
			MenuItem mi = myMenu.findItem(R.id.menu_add_point);
			mi.setVisible(true);
			mi = myMenu.findItem(R.id.menu_delete);
			mi.setVisible(true);
		}
		
		if (ADD_POINTS_MODE == true) {
			addPoint(arg0);	
			ADD_POINTS_MODE = false;
		}
	}
}
