package asdf.parkme

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import org.json.JSONException
import java.io.IOException
import java.io.InputStream


class GoogleMaps : AppCompatActivity(), GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMarkerDragListener, OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mMarker : Marker
    private var currentPos = LatLng(43.655353, -79.388364) //somewhere dt toronto
    private val streetsFile = "TorontoParkingStreets.json"
    private lateinit var streetMap: MutableMap<Any, MutableMap<Any, Any>>
    private lateinit var streetObject: MutableMap<Any, Any>
    private lateinit var parkingLine: Polyline
    private var isMarkerClicked: Boolean = false

    private val TAG : String = "GoogleMaps"

    private lateinit var placesClient : PlacesClient
    private lateinit var mFusedLocationProviderClient : FusedLocationProviderClient
    private var mLocationPermissionGranted : Boolean = false

    private var MY_PERMISSIONS_REQUEST_READ_CONTACTS : Int = 1
    private var currentZoom : Int = 0
    private val LOCATION_RESULT_ZOOM : Int = 15
    private val ZOOM_INCREMENT : Int = 2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        Places.initialize(this.applicationContext, resources.getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        val streetsJSONString = loadJSONFromAsset(streetsFile)
        streetMap = jacksonObjectMapper().readValue(streetsJSONString)



        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment?

        //Specify the types of place data to return.
        autocompleteFragment?.setPlaceFields(listOf(Place.Field.NAME, Place.Field.LAT_LNG))
        //restrict to ~canada
        autocompleteFragment?.setCountry("CA")



        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment?.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latlng = place.latLng!!
                updateMarkerLocation(latlng, LOCATION_RESULT_ZOOM)
                Log.i(TAG, "Place: " + place.name + ", " + place.latLng)
            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
            }
        })
    }

    private fun parseStreetMap(jsonMap: MutableMap<Any, MutableMap<Any, Any>>) {
        val iter: MutableIterator<Any> = jsonMap.keys.iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            streetObject = jsonMap[key]!!
            try {
                if (streetObject["is_accurate"] == "true") {
                    populateStreetMarker()
                }
            } catch (e: JSONException) {
                Log.i(TAG, "Something went wrong!: $e")
            }
        }
    }

    private fun loadJSONFromAsset(file: String): String {
        return try {
            val istream: InputStream = assets.open(file)
            val size: Int = istream.available()
            val buffer = ByteArray(size)
            istream.read(buffer)
            istream.close()
            String(buffer)
        } catch (ex: IOException) {
            ex.printStackTrace()
            ""
        }
    }

    private fun populateStreetMarker(){
        val latlng = LatLng(streetObject["marker_lat"].toString().toDouble(), streetObject["marker_lng"].toString().toDouble())

        val snippet : String =
            streetObject["side"].toString() + "-side " +
            streetObject["times_and_or_days"].toString() + " " +
            streetObject["maximum_period_permitted"].toString()

        mMap.addMarker(
            MarkerOptions()
                .position(latlng)
                .title(streetObject["highway"].toString())
                .snippet(snippet)
                .draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnMapClickListener(object : OnMapClickListener {
            override fun onMapClick (point: LatLng) {
                parkingLine.remove()
                isMarkerClicked = false
            }
        })

        updateLocationUI()

        mMarker = mMap.addMarker(
            MarkerOptions()
                .position(currentPos)
                .title(currentPos.toString())
                .draggable(true)
        )
        mMarker.tag = 0

        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentPos))

        mMap.setOnMarkerClickListener(this)
        mMap.setOnMarkerDragListener(this)

        parseStreetMap(streetMap)
    }

    override fun onMarkerClick(marker: Marker): Boolean {

//        marker.tag = marker.position.toString()
//        Toast.makeText(
//            this,
//            marker.tag.toString(),
//            Toast.LENGTH_SHORT
//        ).show()
        if(!isMarkerClicked &&
            (streetObject["intersection_a_lat"] !is String) &&
            (streetObject["intersection_b_lat"] !is String)){

            parkingLine = mMap.addPolyline(PolylineOptions()
                .add(LatLng(streetObject["intersection_a_lat"].toString().toDouble(), streetObject["intersection_a_lng"].toString().toDouble()),
                    LatLng(streetObject["intersection_b_lat"].toString().toDouble(), streetObject["intersection_b_lng"].toString().toDouble()))
                .width(5F)
                .color(Color.GREEN))
        }

        isMarkerClicked = true

        return false
    }



    override fun onMarkerDragStart(p0: Marker) {
//        mPos = p0.position
//        mMarker.title = mPos.toString()
    }

    override fun onMarkerDrag(p0: Marker) {
//        mPos = p0.position
//        mMarker.title = mPos.toString()
    }

    override fun onMarkerDragEnd(p0: Marker) {
        currentPos = p0.position
        mMarker.title = currentPos.toString()
    }

    private fun updateMarkerLocation(mPos : LatLng, zoomFactor : Int){
        currentPos = mPos
        mMarker.position = mPos
        mMarker.title = mPos.toString()
        currentZoom = zoomFactor
        updateMapZoom()
    }

    private fun updateLocationUI(){
        if (mLocationPermissionGranted) {
            // TODO: fix this
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
        } else {
            mMap.isMyLocationEnabled = false
            mMap.uiSettings.isMyLocationButtonEnabled = false
            getLocationPermission()
        }
    }

    private fun updateMapZoom(){
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPos, currentZoom.toFloat()))
    }

    fun zoomIn(view : View){
        currentZoom += ZOOM_INCREMENT
        updateMapZoom()
    }

    fun zoomOut(view : View){
        currentZoom -= ZOOM_INCREMENT
        updateMapZoom()
    }

    fun findMe(view : View){
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val geoLocation: Task<Location?> = mFusedLocationProviderClient.lastLocation

            geoLocation.addOnCompleteListener { task: Task<Location?> ->
                if (task.isSuccessful) {
                    val latlng = LatLng(task.result!!.latitude, task.result!!.longitude)
                    updateMarkerLocation(latlng, LOCATION_RESULT_ZOOM)
                } else {
                    val exception = task.exception
                    if (exception is ApiException) {
                        Log.i(TAG, String.format("FindMe function failed to find you."))
                    }
                }
            }
        } else {
            // A local method to request required permissions;
            // See https://developer.android.com/training/permissions/requesting
            getLocationPermission()
        }
    }

//    fun findCurrentPlace(view : View){
//        // Use fields to define the data types to return.
//        val placeFields: List<Place.Field> = listOf(Place.Field.NAME, Place.Field.LAT_LNG)
//
//        // Use the builder to create a FindCurrentPlaceRequest.
//        val request = FindCurrentPlaceRequest.newInstance(placeFields)
//
//        // Call findCurrentPlace and handle the response (first check that the user has granted permission).
//        if (ContextCompat.checkSelfPermission(
//                this,
//                ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            val placeResponse: Task<FindCurrentPlaceResponse?> = placesClient.findCurrentPlace(request)
//            val geoLocation: Task<Location?> = mFusedLocationProviderClient.lastLocation
//
//            geoLocation.addOnCompleteListener { task: Task<Location?> ->
//                if (task.isSuccessful) {
//                    val response = task.result
//
//                    val mPlace = response!!.placeLikelihoods[0].place
//                    updateMarkerLocation(mPlace.latLng, MY_LOCATION_ZOOM.toFloat())
//
//                    Log.i(TAG, String.format("FIRST PLACE: '%s', '%s'", mPlace.toString(), mPlace.latLng.toString()))
//                    findViewById<TextView>(R.id.attributionsText).append("Place found: " + mPlace.name)
//                    findViewById<TextView>(R.id.attributionsText).append("Attributions: " + mPlace.attributions)
//
//                    for (placeLikelihood in response!!.placeLikelihoods) {
//                        Log.i(TAG, String.format("Place '%s' has likelihood: %f",
//                            placeLikelihood.place.name,
//                            placeLikelihood.likelihood))
//                    }
//                } else {
//                    val exception = task.exception
//                    if (exception is ApiException) {
//                        Log.i(TAG, String.format("FindCurrentPlace function failed to find a place near you."))
//                    }
//                }
//            }
//        } else { // A local method to request required permissions;
//            // See https://developer.android.com/training/permissions/requesting
//            getLocationPermission()
//        }
//    }

    private fun getLocationPermission(){
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION), MY_PERMISSIONS_REQUEST_READ_CONTACTS)
            }
        } else {
            // Permission has already been granted
            return
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    mLocationPermissionGranted = true
                } else {
                    return
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }



}
