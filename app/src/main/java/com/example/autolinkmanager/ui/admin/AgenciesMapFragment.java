package com.example.autolinkmanager.ui.admin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.example.autolinkmanager.R;
import com.google.firebase.firestore.*;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class AgenciesMapFragment extends Fragment {

    private MapView mapView;
    private ProgressBar progress;
    private FirebaseFirestore db;

    private static final int REQUEST_LOC = 1234;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_agencies_map, container, false);

        // Configuración OSMDroid
        Configuration.getInstance().load(
                requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext())
        );

        mapView = view.findViewById(R.id.mapView);
        mapView.setMultiTouchControls(true);
        progress = view.findViewById(R.id.progress);

        db = FirebaseFirestore.getInstance();

        // Verificar permisos de ubicación
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOC);
        }

        loadAgencies();

        return view;
    }

    private void loadAgencies() {
        progress.setVisibility(View.VISIBLE);
        db.collection("agencies").get()
                .addOnSuccessListener(snaps -> {
                    progress.setVisibility(View.GONE);
                    if (snaps.isEmpty()) {
                        Toast.makeText(requireContext(), "No hay agencias registradas", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    IMapController controller = mapView.getController();
                    controller.setZoom(12.0);

                    boolean centered = false;

                    for (DocumentSnapshot doc : snaps) {
                        com.google.firebase.firestore.GeoPoint geo = doc.getGeoPoint("location");
                        String name = doc.getString("name");
                        String phone = doc.getString("phone");

                        if (geo == null) continue;

                        Marker marker = new Marker(mapView);
                        marker.setPosition(new GeoPoint(geo.getLatitude(), geo.getLongitude()));
                        marker.setTitle(name != null ? name : "Agencia sin nombre");
                        marker.setSnippet(phone != null ? "Teléfono: " + phone : "");
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                        mapView.getOverlays().add(marker);

                        if (!centered) {
                            controller.setCenter(marker.getPosition());
                            centered = true;
                        }
                    }

                    mapView.invalidate();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] perms,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == REQUEST_LOC && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadAgencies();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}

