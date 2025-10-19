package com.example.autolinkmanager.ui.admin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.autolinkmanager.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;

public class PickAgencyLocationFragment extends Fragment {

    private MapView mapView;
    private Marker marker;
    private Button btnSave, btnCancel;

    private String agencyId;
    private Double initLat, initLng;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final int REQ_LOC = 3101;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_pick_agency_location, container, false);

        // Leer argumentos de forma segura
        Bundle args = getArguments();
        if (args != null) {
            agencyId = args.getString("agencyId", null);
            if (args.containsKey("lat")) initLat = (double) args.getFloat("lat");
            if (args.containsKey("lng")) initLng = (double) args.getFloat("lng");
        }

        if (agencyId == null || agencyId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Falta el ID de agencia", Toast.LENGTH_LONG).show();
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp();
            return v; // ó return inflater.inflate(...); pero ya salimos
        }


        // OSMDroid config
        Configuration.getInstance().load(requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext()));

        mapView = v.findViewById(R.id.mapView);
        mapView.setMultiTouchControls(true);

        btnSave = v.findViewById(R.id.btnSave);
        btnCancel = v.findViewById(R.id.btnCancel);

        // Centrar cámara
        IMapController controller = mapView.getController();
        controller.setZoom(15.0);

        org.osmdroid.util.GeoPoint center;
        if (initLat != null && initLng != null) {
            center = new org.osmdroid.util.GeoPoint(initLat, initLng);
        } else {
            // Centro por defecto: CDMX
            center = new org.osmdroid.util.GeoPoint(19.432608, -99.133209);
        }
        controller.setCenter(center);

        // Si ya hay coord inicial, muestra marcador
        if (initLat != null && initLng != null) {
            marker = new Marker(mapView);
            marker.setPosition(new org.osmdroid.util.GeoPoint(initLat, initLng));
            marker.setTitle("Nueva ubicación");
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
        }

        // Tap largo para colocar/mover marcador
        MapEventsOverlay tapOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(org.osmdroid.util.GeoPoint p) { return false; }
            @Override public boolean longPressHelper(org.osmdroid.util.GeoPoint p) {
                placeOrMoveMarker(p);
                return true;
            }
        });
        mapView.getOverlays().add(tapOverlay);

        btnCancel.setOnClickListener(v1 -> NavHostFragment.findNavController(this).navigateUp());
        btnSave.setOnClickListener(v12 -> saveAndExit());

        // (Opcional) habilitar mi ubicación si tienes permiso
        enableMyLocationIfGranted();

        return v;
    }

    private void placeOrMoveMarker(org.osmdroid.util.GeoPoint point) {
        if (marker == null) {
            marker = new Marker(mapView);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
        }
        marker.setPosition(point);
        marker.setTitle("Nueva ubicación");
        mapView.invalidate();
    }

    private void saveAndExit() {
        if (marker == null) {
            Toast.makeText(requireContext(), "Mantén presionado en el mapa para seleccionar una ubicación", Toast.LENGTH_SHORT).show();
            return;
        }
        double lat = marker.getPosition().getLatitude();
        double lng = marker.getPosition().getLongitude();

        db.collection("agencies").document(agencyId)
                .update("location", new GeoPoint(lat, lng))
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Ubicación actualizada", Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(this).navigateUp();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void enableMyLocationIfGranted() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            return;
        }
        // Si quieres, aquí puedes mover la cámara a la última ubicación conocida (usando FusedLocation)
        // pero OSMDroid no muestra el "punto azul" nativo, eso es extra.
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        // No es indispensable hacer nada aquí; el mapa funciona igual.
    }

    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
}
