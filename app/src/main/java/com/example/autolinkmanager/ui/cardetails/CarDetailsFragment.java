package com.example.autolinkmanager.ui.cardetails;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.Auto;
import com.example.autolinkmanager.Hojalateria;
import com.example.autolinkmanager.Mantenimiento;
import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CarDetailsFragment extends Fragment {

    private static final String TAG = "CarDetailsFragment";

    // Data passed to the fragment
    private String vehicleDocId; // The ID of the car document in Firebase
    private String currentAgencyId; // ID of the logged-in user's agency

    // Firebase & Formatters
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormatter;
    private NumberFormat currencyFormatter;

    // UI Views
    private ImageView ivCarPhotoDetails;
    private TextView tvDetailPlaca, tvDetailModelo, tvDetailAnio, tvDetailPropietario, tvDetailTelefono;
    private LinearLayout layoutServiceDetails;
    private TextView tvNoServiceInfo;
    private TextView tvDetailServiceType, tvDetailFechaIngreso, tvDetailFechaSalida, tvDetailCosto, tvDetailEstadoPago, tvDetailNotas, tvDetailNotasLabel;
    private LinearLayout layoutHojalateriaDetails;
    private View viewDetailColorPreview;
    private TextView tvDetailColorHex, tvDetailTiposTrabajo;

    // State variables for payment status logic
    private boolean originalPaymentStatus = false; // Status when the screen loaded
    private boolean currentPaymentStatus = false; // Status as the user clicks (can change)
    private boolean paymentStatusChanged = false; // Flag to track if changes were made

    public CarDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Firebase, formatters
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "MX")); // MXN currency format

        // Get the vehicle document ID passed from the previous fragment
        if (getArguments() != null) {
            vehicleDocId = getArguments().getString("vehicle_doc_id");
        }

        // Intercept the back button press to check for unsaved changes
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                confirmExit(); // Call the method to handle exit confirmation
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_car_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- Find all UI views by their IDs ---
        ivCarPhotoDetails = view.findViewById(R.id.iv_car_photo_details);
        tvDetailPlaca = view.findViewById(R.id.tv_detail_placa);
        tvDetailModelo = view.findViewById(R.id.tv_detail_modelo);
        tvDetailAnio = view.findViewById(R.id.tv_detail_anio);
        tvDetailPropietario = view.findViewById(R.id.tv_detail_propietario);
        tvDetailTelefono = view.findViewById(R.id.tv_detail_telefono);
        layoutServiceDetails = view.findViewById(R.id.layout_service_details);
        tvNoServiceInfo = view.findViewById(R.id.tv_no_service_info);
        tvDetailServiceType = view.findViewById(R.id.tv_detail_service_type);
        tvDetailFechaIngreso = view.findViewById(R.id.tv_detail_fecha_ingreso);
        tvDetailFechaSalida = view.findViewById(R.id.tv_detail_fecha_salida);
        tvDetailCosto = view.findViewById(R.id.tv_detail_costo);
        tvDetailEstadoPago = view.findViewById(R.id.tv_detail_estado_pago);
        tvDetailNotas = view.findViewById(R.id.tv_detail_notas);
        tvDetailNotasLabel = view.findViewById(R.id.tv_detail_notas_label);
        layoutHojalateriaDetails = view.findViewById(R.id.layout_hojalateria_details);
        viewDetailColorPreview = view.findViewById(R.id.view_detail_color_preview);
        tvDetailColorHex = view.findViewById(R.id.tv_detail_color_hex);
        tvDetailTiposTrabajo = view.findViewById(R.id.tv_detail_tipos_trabajo);

        // --- Set the click listener for the payment status TextView ---
        tvDetailEstadoPago.setOnClickListener(v -> togglePaymentStatus());

        // Start loading data by first getting the user's agency ID
        loadUserAgencyId();
    }

    // Fetches the agency ID for the current logged-in user
    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document != null && document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null && user.getAgencyId() != null) {
                                    currentAgencyId = user.getAgencyId();
                                    Log.d(TAG, "Agency ID loaded: " + currentAgencyId);
                                    loadCarDetails(); // Proceed to load car details
                                } else {
                                    Toast.makeText(getContext(), "User data incomplete (no agency ID).", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "User document not found.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Error fetching user data", task.getException());
                            Toast.makeText(getContext(), "Error loading user data.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(getContext(), "User not authenticated.", Toast.LENGTH_SHORT).show();
            // Optionally navigate to login screen
        }
    }

    // Fetches the specific car/service document from Firestore
    private void loadCarDetails() {
        if (currentAgencyId == null || vehicleDocId == null) {
            Log.e(TAG, "Cannot load details: agencyId or vehicleDocId is null.");
            Toast.makeText(getContext(), "Error: Missing required data.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Loading details for doc ID: " + vehicleDocId + " in agency: " + currentAgencyId);
        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles").document(vehicleDocId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            Log.d(TAG, "Document data: " + document.getData());

                            // Load common Auto fields
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Long anioLong = document.getLong("anio"); // Read as Long first
                            int anio = (anioLong != null) ? anioLong.intValue() : 0;
                            String nombrePropietario = document.getString("nombrePropietario");
                            String telefonoPropietario = document.getString("telefonoPropietario");
                            String fotoBase64 = document.getString("fotoBase64");

                            // Display common Auto info
                            tvDetailPlaca.setText(placa != null ? placa : "N/A");
                            tvDetailModelo.setText(modelo != null ? modelo : "N/A");
                            tvDetailAnio.setText(anio != 0 ? String.valueOf(anio) : "N/A");
                            tvDetailPropietario.setText(nombrePropietario != null && !nombrePropietario.isEmpty() ? nombrePropietario : "N/A");
                            tvDetailTelefono.setText(telefonoPropietario != null && !telefonoPropietario.isEmpty() ? telefonoPropietario : "N/A");

                            // Decode and display photo
                            if (fotoBase64 != null && !fotoBase64.isEmpty()) {
                                Bitmap bitmap = base64ToBitmap(fotoBase64);
                                if (bitmap != null) {
                                    ivCarPhotoDetails.setImageBitmap(bitmap);
                                } else {
                                    ivCarPhotoDetails.setImageResource(R.drawable.outline_directions_car_24); // Placeholder on decode error
                                }
                            } else {
                                ivCarPhotoDetails.setImageResource(R.drawable.outline_directions_car_24); // Placeholder if no photo
                            }

                            // Determine service type and display details
                            String tipoMantenimiento = document.getString("tipoMantenimiento");
                            boolean isHojalateria = document.contains("tiposTrabajo"); // Check for a field unique to Hojalateria

                            if (tipoMantenimiento != null && !tipoMantenimiento.isEmpty()) {
                                Mantenimiento mantenimiento = document.toObject(Mantenimiento.class);
                                if (mantenimiento != null) displayServiceDetails(mantenimiento); else handleDeserializationError("Mantenimiento");
                            } else if (isHojalateria) {
                                Hojalateria hojalateria = document.toObject(Hojalateria.class);
                                if (hojalateria != null) displayServiceDetails(hojalateria); else handleDeserializationError("Hojalateria");
                            } else {
                                // No specific service data found
                                layoutServiceDetails.setVisibility(View.GONE);
                                tvNoServiceInfo.setVisibility(View.VISIBLE);
                                tvNoServiceInfo.setText("No hay información de servicio registrada.");
                                Log.w(TAG,"No specific service fields (tipoMantenimiento or tiposTrabajo) found.");
                            }

                        } else {
                            Log.w(TAG,"Vehicle document not found: " + vehicleDocId);
                            Toast.makeText(getContext(), "Vehículo no encontrado.", Toast.LENGTH_SHORT).show();
                            layoutServiceDetails.setVisibility(View.GONE);
                            tvNoServiceInfo.setVisibility(View.VISIBLE);
                            tvNoServiceInfo.setText("Vehículo no encontrado.");
                        }
                    } else {
                        Log.e(TAG, "Error getting document: ", task.getException());
                        Toast.makeText(getContext(), "Error al cargar los detalles del vehículo.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Converts a Base64 string to a Bitmap image
    private Bitmap base64ToBitmap(String base64String) {
        if (base64String == null || base64String.isEmpty()) return null;
        try {
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decoding Base64 string: " + e.getMessage());
            return null;
        }
    }

    // Displays the service details in the UI based on the object type (Mantenimiento or Hojalateria)
    private void displayServiceDetails(Auto serviceObject) {
        layoutServiceDetails.setVisibility(View.VISIBLE);
        tvNoServiceInfo.setVisibility(View.GONE);
        layoutHojalateriaDetails.setVisibility(View.GONE); // Hide Hojalateria details by default

        // Common service variables
        Date fechaIngreso = null;
        Date fechaSalida = null;
        double costo = 0.0;
        boolean pagado = false; // Assume not paid initially
        String notas = "";
        String serviceTypeName = "Servicio Desconocido";

        // Extract data based on the actual class type
        if (serviceObject instanceof Mantenimiento) {
            Mantenimiento m = (Mantenimiento) serviceObject;
            serviceTypeName = "Mantenimiento: " + (m.getTipoMantenimiento() != null ? m.getTipoMantenimiento() : "N/A");
            fechaIngreso = m.getFechaIngreso();
            fechaSalida = m.getFechaSalida();
            costo = m.getCosto();
            pagado = m.isPagado();
            notas = m.getNotas();

        } else if (serviceObject instanceof Hojalateria) {
            Hojalateria h = (Hojalateria) serviceObject;
            serviceTypeName = "Servicio: Hojalatería";
            fechaIngreso = h.getFechaIngreso();
            fechaSalida = h.getFechaSalida();
            costo = h.getCosto();
            pagado = h.isPagado();
            notas = h.getNotas();

            // Display Hojalateria specific details
            layoutHojalateriaDetails.setVisibility(View.VISIBLE);
            // Color
            if (h.getColorHex() != null) {
                try {
                    String colorString = h.getColorHex().startsWith("#") ? h.getColorHex() : "#" + h.getColorHex();
                    viewDetailColorPreview.setBackgroundColor(Color.parseColor(colorString));
                    tvDetailColorHex.setText(h.getColorHex());
                } catch (IllegalArgumentException e) {
                    viewDetailColorPreview.setBackgroundColor(Color.GRAY); // Fallback color
                    tvDetailColorHex.setText("Inválido");
                }
            } else {
                viewDetailColorPreview.setBackgroundColor(Color.TRANSPARENT);
                tvDetailColorHex.setText("N/A");
            }
            // Tipos de trabajo
            if (h.getTiposTrabajo() != null && !h.getTiposTrabajo().isEmpty()) {
                tvDetailTiposTrabajo.setText("Trabajo(s): " + String.join(", ", h.getTiposTrabajo()));
                tvDetailTiposTrabajo.setVisibility(View.VISIBLE);
            } else {
                tvDetailTiposTrabajo.setVisibility(View.GONE);
            }

        } else {
            // Should not happen if data is consistent
            handleDeserializationError("Unknown Service Type");
            return;
        }

        // --- STORE INITIAL PAYMENT STATUS AND UPDATE UI ---
        originalPaymentStatus = pagado;
        currentPaymentStatus = pagado;
        paymentStatusChanged = false; // Reset change flag on load
        updatePaymentStatusUI(currentPaymentStatus); // Update the TextView

        // Display common service details in the UI
        tvDetailServiceType.setText(serviceTypeName);
        tvDetailFechaIngreso.setText(fechaIngreso != null ? dateFormatter.format(fechaIngreso) : "N/A");
        tvDetailFechaSalida.setText(fechaSalida != null ? dateFormatter.format(fechaSalida) : "N/A");
        tvDetailCosto.setText(currencyFormatter.format(costo));

        // Display notes if they exist
        if (notas != null && !notas.isEmpty()) {
            tvDetailNotas.setText(notas);
            tvDetailNotasLabel.setVisibility(View.VISIBLE);
            tvDetailNotas.setVisibility(View.VISIBLE);
        } else {
            tvDetailNotasLabel.setVisibility(View.GONE);
            tvDetailNotas.setVisibility(View.GONE);
        }
    }

    // Handles errors during Firestore data-to-object conversion
    private void handleDeserializationError(String objectType){
        Log.e(TAG, "Error deserializing DocumentSnapshot to " + objectType + ". Check Firestore fields and Java class.");
        Toast.makeText(getContext(), "Error reading service data.", Toast.LENGTH_SHORT).show();
        layoutServiceDetails.setVisibility(View.GONE);
        tvNoServiceInfo.setVisibility(View.VISIBLE);
        tvNoServiceInfo.setText("Error reading service data.");
    }

    // Toggles the payment status when the TextView is clicked
    private void togglePaymentStatus() {
        if (!layoutServiceDetails.isShown()) return; // Do nothing if service details aren't visible

        currentPaymentStatus = !currentPaymentStatus; // Flip the current state
        // Mark as changed only if the current state differs from the originally loaded state
        paymentStatusChanged = (currentPaymentStatus != originalPaymentStatus);
        updatePaymentStatusUI(currentPaymentStatus); // Update the TextView's appearance
    }

    // Updates the payment status TextView (text and background)
    private void updatePaymentStatusUI(boolean isPaid) {
        tvDetailEstadoPago.setText(isPaid ? "Pagado" : "Pendiente");
        tvDetailEstadoPago.setBackgroundResource(isPaid ? R.drawable.rounded_tag_active : R.drawable.rounded_tag_pending);
    }

    // Shows a confirmation dialog if payment status changed before navigating back
    private void confirmExit() {
        if (paymentStatusChanged) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Guardar Cambios")
                    .setMessage("El estado de pago ha cambiado. ¿Deseas guardar los cambios antes de salir?")
                    .setPositiveButton("Guardar", (dialog, which) -> {
                        savePaymentStatusToFirestore(); // Save changes and then navigate back
                    })
                    .setNegativeButton("No Guardar", (dialog, which) -> {
                        navigateBack(); // Discard changes and navigate back
                    })
                    .setNeutralButton("Cancelar", null) // Stay on the screen
                    .show();
        } else {
            // No changes were made, navigate back directly
            navigateBack();
        }
    }

    // Saves the current payment status to Firestore
    private void savePaymentStatusToFirestore() {
        if (currentAgencyId == null || vehicleDocId == null) {
            Toast.makeText(getContext(), "Error: Cannot save changes.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("pagado", currentPaymentStatus); // Field to update and the new value

        Log.d(TAG, "Updating payment status to: " + currentPaymentStatus + " for doc: " + vehicleDocId);
        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles").document(vehicleDocId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Estado de pago actualizado.", Toast.LENGTH_SHORT).show();
                    paymentStatusChanged = false; // Reset the flag as changes are saved
                    originalPaymentStatus = currentPaymentStatus; // The current status is now the original
                    navigateBack(); // Navigate back after successful save
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error updating payment status", e);
                    // Optionally ask user if they want to retry or exit without saving
                });
    }

    // Navigates back to the previous fragment in the stack
    private void navigateBack() {
        // Use requireView() safely within onViewCreated and subsequent calls
        if(getView() != null) {
            NavController navController = Navigation.findNavController(requireView());
            navController.popBackStack(); // Go back to CarListFragment
        } else {
            Log.e(TAG, "navigateBack called when view is null");
        }
    }
}