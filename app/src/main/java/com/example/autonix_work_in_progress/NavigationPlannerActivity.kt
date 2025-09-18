package com.example.autonix_work_in_progress

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*

class NavigationPlannerActivity : AppCompatActivity() {

    // Views
    private lateinit var mapView: MapView
    private lateinit var btnBack: ImageView
    private lateinit var btnMyLocation: ImageView
    private lateinit var btnLayers: ImageView
    private lateinit var searchLayout: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var routeInfoPanel: LinearLayout
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var btnSaveRoute: ImageView
    private lateinit var btnStartTrip: LinearLayout

    // FABs
    private lateinit var fabMain: FloatingActionButton
    private lateinit var fabSearch: FloatingActionButton
    private lateinit var fabLoad: FloatingActionButton
    private lateinit var labelSearch: TextView
    private lateinit var labelLoad: TextView

    // Map components
    private lateinit var mapController: IMapController
    private var currentRoute: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    // State
    private var isFabExpanded = false
    private var isSearchVisible = false
    private var currentRouteData: RouteData? = null

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Search
    private lateinit var searchAdapter: SearchResultsAdapter
    private val searchResults = mutableListOf<SearchResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", 0))

        setContentView(R.layout.activity_navigation_planner)

        initFirebase()
        initViews()
        initMap()
        setupClickListeners()
        setupSearch()
    }

    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btnBack)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        btnLayers = findViewById(R.id.btnLayers)
        searchLayout = findViewById(R.id.searchLayout)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        routeInfoPanel = findViewById(R.id.routeInfoPanel)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        btnSaveRoute = findViewById(R.id.btnSaveRoute)
        btnStartTrip = findViewById(R.id.btnStartTrip)

        fabMain = findViewById(R.id.fabMain)
        fabSearch = findViewById(R.id.fabSearch)
        fabLoad = findViewById(R.id.fabLoad)
        labelSearch = findViewById(R.id.labelSearch)
        labelLoad = findViewById(R.id.labelLoad)
    }

    private fun initMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapController = mapView.controller
        mapController.setZoom(15.0)

        // Set initial location (Mumbai)
        val mumbai = GeoPoint(19.0760, 72.8777)
        mapController.setCenter(mumbai)

        // Map click listener for setting waypoints
        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                handleMapClick(geoPoint)
            }
            false
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnMyLocation.setOnClickListener {
            // Move to user's current location
            Toast.makeText(this, "Moving to your location...", Toast.LENGTH_SHORT).show()
        }

        btnLayers.setOnClickListener {
            showLayerDialog()
        }

        fabMain.setOnClickListener {
            toggleFabMenu()
        }

        fabSearch.setOnClickListener {
            showSearchBar()
            collapseFabMenu()
        }

        fabLoad.setOnClickListener {
            showLoadRouteDialog()
            collapseFabMenu()
        }

        btnClearSearch.setOnClickListener {
            hideSearchBar()
        }

        btnSaveRoute.setOnClickListener {
            saveCurrentRoute()
        }

        btnStartTrip.setOnClickListener {
            startTrip()
        }
    }

    private fun setupSearch() {
        searchAdapter = SearchResultsAdapter(searchResults) { result ->
            handleSearchResultClick(result)
        }
        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvSearchResults.adapter = searchAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                btnClearSearch.isVisible = query.isNotEmpty()

                if (query.length >= 3) {
                    searchPlaces(query)
                } else {
                    hideSearchResults()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun toggleFabMenu() {
        if (isFabExpanded) {
            collapseFabMenu()
        } else {
            expandFabMenu()
        }
    }

    private fun expandFabMenu() {
        isFabExpanded = true

        // Change main FAB icon to X
        fabMain.setImageResource(R.drawable.ic_close)

        // Show sub FABs
        fabSearch.visibility = View.VISIBLE
        fabLoad.visibility = View.VISIBLE
        labelSearch.visibility = View.VISIBLE
        labelLoad.visibility = View.VISIBLE

        // Animate sub FABs
        val searchAnimSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fabSearch, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(fabSearch, "translationY", 100f, 0f),
                ObjectAnimator.ofFloat(labelSearch, "alpha", 0f, 1f)
            )
            duration = 300
        }

        val loadAnimSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fabLoad, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(fabLoad, "translationY", 160f, 0f),
                ObjectAnimator.ofFloat(labelLoad, "alpha", 0f, 1f)
            )
            duration = 300
            startDelay = 50
        }

        searchAnimSet.start()
        loadAnimSet.start()
    }

    private fun collapseFabMenu() {
        isFabExpanded = false

        // Change main FAB icon back to +
        fabMain.setImageResource(R.drawable.ic_add)

        // Animate sub FABs out
        val animSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fabSearch, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(fabLoad, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(labelSearch, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(labelLoad, "alpha", 1f, 0f)
            )
            duration = 200
        }

        animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                fabSearch.visibility = View.INVISIBLE
                fabLoad.visibility = View.INVISIBLE
                labelSearch.visibility = View.INVISIBLE
                labelLoad.visibility = View.INVISIBLE
            }
        })

        animSet.start()
    }

    private fun showSearchBar() {
        if (isSearchVisible) return

        isSearchVisible = true
        searchLayout.visibility = View.VISIBLE

        val slideDown = ObjectAnimator.ofFloat(searchLayout, "translationY", -50f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(searchLayout, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(slideDown, fadeIn)
            duration = 300
            start()
        }

        etSearch.requestFocus()
        showKeyboard()
    }

    private fun hideSearchBar() {
        isSearchVisible = false

        val slideUp = ObjectAnimator.ofFloat(searchLayout, "translationY", 0f, -50f)
        val fadeOut = ObjectAnimator.ofFloat(searchLayout, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(slideUp, fadeOut)
            duration = 300
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    searchLayout.visibility = View.GONE
                    etSearch.text.clear()
                    hideSearchResults()
                }
            })
            start()
        }

        hideKeyboard()
    }

    private fun searchPlaces(query: String) {
        // Simulate search results - in real app, use Nominatim API or similar
        val mockResults = listOf(
            SearchResult("Mumbai Central", "Mumbai, Maharashtra", GeoPoint(19.0176, 72.8562)),
            SearchResult("Bandra West", "Mumbai, Maharashtra", GeoPoint(19.0596, 72.8295)),
            SearchResult("Andheri East", "Mumbai, Maharashtra", GeoPoint(19.1136, 72.8697))
        ).filter { it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true) }

        searchResults.clear()
        searchResults.addAll(mockResults)
        searchAdapter.notifyDataSetChanged()

        rvSearchResults.isVisible = mockResults.isNotEmpty()
    }

    private fun hideSearchResults() {
        rvSearchResults.visibility = View.GONE
    }

    private fun handleSearchResultClick(result: SearchResult) {
        hideSearchResults()
        hideSearchBar()

        // Move map to selected location
        mapController.animateTo(result.location)
        mapController.setZoom(17.0)

        // Add marker or set as waypoint
        handleMapClick(result.location)
    }

    private fun handleMapClick(geoPoint: GeoPoint) {
        if (startMarker == null) {
            // Set start point
            startMarker = createMarker(geoPoint, "Start", R.drawable.ic_start_flag)
            mapView.overlays.add(startMarker)
        } else if (endMarker == null) {
            // Set end point and calculate route
            endMarker = createMarker(geoPoint, "Destination", R.drawable.ic_finish_flag)
            mapView.overlays.add(endMarker)
            calculateRoute(startMarker!!.position, endMarker!!.position)
        } else {
            // Clear and start new route
            clearRoute()
            startMarker = createMarker(geoPoint, "Start", R.drawable.ic_start_flag)
            mapView.overlays.add(startMarker)
        }

        mapView.invalidate()
    }

    private fun createMarker(geoPoint: GeoPoint, title: String, iconRes: Int): Marker {
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = title
        marker.icon = ContextCompat.getDrawable(this, iconRes)
        return marker
    }

    private fun calculateRoute(start: GeoPoint, end: GeoPoint) {
        // Simulate route calculation - in real app, use OSRM or similar
        val routePoints = listOf(start, end) // Simplified

        currentRoute = Polyline().apply {
            setPoints(routePoints)
            outlinePaint.color = ContextCompat.getColor(this@NavigationPlannerActivity, R.color.route_color)
            outlinePaint.strokeWidth = 12f
        }

        mapView.overlays.add(currentRoute)
        mapView.invalidate()

        // Show route info
        currentRouteData = RouteData(
            startPoint = start,
            endPoint = end,
            distance = calculateDistance(start, end),
            duration = calculateDuration(start, end),
            routePoints = routePoints
        )

        showRouteInfo()
    }

    private fun calculateDistance(start: GeoPoint, end: GeoPoint): Double {
        // Simplified distance calculation
        return start.distanceToAsDouble(end) / 1000.0 // Convert to km
    }

    private fun calculateDuration(start: GeoPoint, end: GeoPoint): Int {
        // Simplified duration calculation (assume 40 km/h average)
        return (calculateDistance(start, end) * 1.5).toInt() // minutes
    }

    private fun showRouteInfo() {
        currentRouteData?.let { route ->
            tvDistance.text = String.format("%.1f km", route.distance)
            tvDuration.text = "${route.duration} min"

            routeInfoPanel.visibility = View.VISIBLE

            val slideUp = ObjectAnimator.ofFloat(routeInfoPanel, "translationY", 100f, 0f)
            val fadeIn = ObjectAnimator.ofFloat(routeInfoPanel, "alpha", 0f, 1f)

            AnimatorSet().apply {
                playTogether(slideUp, fadeIn)
                duration = 300
                start()
            }
        }
    }

    private fun clearRoute() {
        startMarker?.let { mapView.overlays.remove(it) }
        endMarker?.let { mapView.overlays.remove(it) }
        currentRoute?.let { mapView.overlays.remove(it) }

        startMarker = null
        endMarker = null
        currentRoute = null
        currentRouteData = null

        routeInfoPanel.visibility = View.GONE
        mapView.invalidate()
    }

    private fun saveCurrentRoute() {
        currentRouteData?.let { route ->
            val userId = auth.currentUser?.uid ?: return

            val routeDoc = hashMapOf(
                "userId" to userId,
                "name" to "Route ${System.currentTimeMillis()}",
                "startLat" to route.startPoint.latitude,
                "startLng" to route.startPoint.longitude,
                "endLat" to route.endPoint.latitude,
                "endLng" to route.endPoint.longitude,
                "distance" to route.distance,
                "duration" to route.duration,
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("saved_routes")
                .add(routeDoc)
                .addOnSuccessListener {
                    Toast.makeText(this, "Route saved successfully!", Toast.LENGTH_SHORT).show()
                    btnSaveRoute.setImageResource(R.drawable.ic_bookmark_border)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save route: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startTrip() {
        currentRouteData?.let { route ->
            val userId = auth.currentUser?.uid ?: return

            val tripDoc = hashMapOf(
                "userId" to userId,
                "startLat" to route.startPoint.latitude,
                "startLng" to route.startPoint.longitude,
                "endLat" to route.endPoint.latitude,
                "endLng" to route.endPoint.longitude,
                "distance" to route.distance,
                "estimatedDuration" to route.duration,
                "startTime" to System.currentTimeMillis(),
                "status" to "active"
            )

            firestore.collection("active_trips")
                .add(tripDoc)
                .addOnSuccessListener { documentRef ->
                    Toast.makeText(this, "Trip started!", Toast.LENGTH_SHORT).show()
                    // Navigate to active trip view or fleet control
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to start trip: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showLoadRouteDialog() {
        // TODO: Implement load route dialog
        Toast.makeText(this, "Load route dialog - coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun showLayerDialog() {
        val layers = arrayOf("Standard", "Satellite", "Terrain", "Traffic")
        android.app.AlertDialog.Builder(this)
            .setTitle("Map Layers")
            .setItems(layers) { _, which ->
                when (which) {
                    0 -> mapView.setTileSource(TileSourceFactory.MAPNIK)
                    1 -> Toast.makeText(this, "Satellite layer - coming soon!", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Terrain layer - coming soon!", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "Traffic layer - coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // Data classes
    data class RouteData(
        val startPoint: GeoPoint,
        val endPoint: GeoPoint,
        val distance: Double,
        val duration: Int,
        val routePoints: List<GeoPoint>
    )

    data class SearchResult(
        val name: String,
        val address: String,
        val location: GeoPoint
    )
}