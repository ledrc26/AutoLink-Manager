package com.example.autolinkmanager.ui.cardetails;

import androidx.lifecycle.ViewModelProvider;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
import java.util.Locale;

public class CarDetailsFragment extends Fragment {

    private static final String TAG = "CarDetailsFragment";

    private String vehicleDocId; // El ID del documento del auto en Firebase
    private String currentAgencyId; // ID de la agencia del usuario actual

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormatter;
    private NumberFormat currencyFormatter;

    // Vistas de Auto
    private ImageView ivCarPhotoDetails;
    private TextView tvDetailPlaca, tvDetailModelo, tvDetailAnio, tvDetailPropietario, tvDetailTelefono;

    // Vistas de Servicio
    private LinearLayout layoutServiceDetails;
    private TextView tvNoServiceInfo;
    private TextView tvDetailServiceType, tvDetailFechaIngreso, tvDetailFechaSalida, tvDetailCosto, tvDetailEstadoPago, tvDetailNotas, tvDetailNotasLabel;

    // Vistas específicas de Hojalatería
    private LinearLayout layoutHojalateriaDetails;
    private View viewDetailColorPreview;
    private TextView tvDetailColorHex, tvDetailTiposTrabajo;

    public CarDetailsFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "MX")); // Ejemplo para México

        if (getArguments() != null) {
            vehicleDocId = getArguments().getString("vehicle_doc_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_car_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar vistas del auto
        ivCarPhotoDetails = view.findViewById(R.id.iv_car_photo_details);
        tvDetailPlaca = view.findViewById(R.id.tv_detail_placa);
        tvDetailModelo = view.findViewById(R.id.tv_detail_modelo);
        tvDetailAnio = view.findViewById(R.id.tv_detail_anio);
        tvDetailPropietario = view.findViewById(R.id.tv_detail_propietario);
        tvDetailTelefono = view.findViewById(R.id.tv_detail_telefono);

        // Inicializar vistas del servicio
        layoutServiceDetails = view.findViewById(R.id.layout_service_details);
        tvNoServiceInfo = view.findViewById(R.id.tv_no_service_info);
        tvDetailServiceType = view.findViewById(R.id.tv_detail_service_type);
        tvDetailFechaIngreso = view.findViewById(R.id.tv_detail_fecha_ingreso);
        tvDetailFechaSalida = view.findViewById(R.id.tv_detail_fecha_salida);
        tvDetailCosto = view.findViewById(R.id.tv_detail_costo);
        tvDetailEstadoPago = view.findViewById(R.id.tv_detail_estado_pago);
        tvDetailNotas = view.findViewById(R.id.tv_detail_notas);
        tvDetailNotasLabel = view.findViewById(R.id.tv_detail_notas_label);

        // Inicializar vistas específicas de Hojalatería
        layoutHojalateriaDetails = view.findViewById(R.id.layout_hojalateria_details);
        viewDetailColorPreview = view.findViewById(R.id.view_detail_color_preview);
        tvDetailColorHex = view.findViewById(R.id.tv_detail_color_hex);
        tvDetailTiposTrabajo = view.findViewById(R.id.tv_detail_tipos_trabajo);

        loadUserAgencyId();
    }

    private void loadUserAgencyId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.collection("users").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                User user = document.toObject(User.class);
                                if (user != null && user.getAgencyId() != null) {
                                    currentAgencyId = user.getAgencyId();
                                    loadCarDetails(); // Cargar detalles después de obtener agencyId
                                } else {
                                    Toast.makeText(getContext(), "Usuario sin ID de agencia.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "Documento de usuario no encontrado.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Error al cargar usuario", task.getException());
                            Toast.makeText(getContext(), "Error al cargar datos de usuario.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(getContext(), "Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            // Considerar navegar a la pantalla de login si el usuario no está autenticado
        }
    }

    private void loadCarDetails() {
        if (currentAgencyId == null || vehicleDocId == null) {
            Toast.makeText(getContext(), "Error: faltan datos para cargar los detalles.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("agencies").document(currentAgencyId)
                .collection("vehicles").document(vehicleDocId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Intentar convertir a Mantenimiento o Hojalateria
                            // Necesitas un campo en Firebase que te diga qué tipo de servicio es
                            // Por ahora, intentaremos ambos y veremos cuál tiene campos específicos

                            // Campos comunes a Auto, Mantenimiento y Hojalateria
                            String placa = document.getString("placa");
                            String modelo = document.getString("modelo");
                            Long anioLong = document.getLong("anio"); // Lee directamente como Long
                            int anio = (anioLong != null) ? anioLong.intValue() : 0;
                            String nombrePropietario = document.getString("nombrePropietario");
                            String telefonoPropietario = document.getString("telefonoPropietario");
                            String fotoBase64 = document.getString("fotoBase64");

                            // Mostrar información del auto
                            tvDetailPlaca.setText(placa);
                            tvDetailModelo.setText(modelo);
                            tvDetailAnio.setText(String.valueOf(anio));
                            tvDetailPropietario.setText(nombrePropietario != null && !nombrePropietario.isEmpty() ? nombrePropietario : "N/A");
                            tvDetailTelefono.setText(telefonoPropietario != null && !telefonoPropietario.isEmpty() ? telefonoPropietario : "N/A");

                            if (fotoBase64 != null && !fotoBase64.isEmpty()) {
                                ivCarPhotoDetails.setImageBitmap(base64ToBitmap(fotoBase64));
                            } else {
                                ivCarPhotoDetails.setImageResource(R.drawable.outline_directions_car_24); // Asegúrate de tener un placeholder
                            }

                            // Intentar cargar como Mantenimiento
                            String tipoMantenimiento = document.getString("tipoMantenimiento");
                            if (tipoMantenimiento != null && !tipoMantenimiento.isEmpty()) {
                                Mantenimiento mantenimiento = document.toObject(Mantenimiento.class);
                                displayServiceDetails(mantenimiento);
                            }
                            // Intentar cargar como Hojalateria
                            else if (document.contains("tiposTrabajo")) { // Hojalateria tiene este campo
                                Hojalateria hojalateria = document.toObject(Hojalateria.class);
                                displayServiceDetails(hojalateria);
                            } else {
                                // No se encontró información de servicio
                                layoutServiceDetails.setVisibility(View.GONE);
                                tvNoServiceInfo.setVisibility(View.VISIBLE);
                            }

                        } else {
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

    // Método para convertir Base64 a Bitmap
    private Bitmap base64ToBitmap(String base64String) {
        try {
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decodificando Base64: " + e.getMessage());
            return null;
        }
    }

    // Método genérico para mostrar detalles del servicio (Mantenimiento o Hojalateria)
    private void displayServiceDetails(Auto serviceObject) {
        layoutServiceDetails.setVisibility(View.VISIBLE);
        tvNoServiceInfo.setVisibility(View.GONE);
        layoutHojalateriaDetails.setVisibility(View.GONE); // Ocultar por defecto

        Date fechaIngreso = null;
        Date fechaSalida = null;
        double costo = 0.0;
        boolean pagado = false;
        String notas = "";
        String serviceTypeName = "";

        if (serviceObject instanceof Mantenimiento) {
            Mantenimiento mantenimiento = (Mantenimiento) serviceObject;
            serviceTypeName = "Mantenimiento: " + mantenimiento.getTipoMantenimiento();
            fechaIngreso = mantenimiento.getFechaIngreso();
            fechaSalida = mantenimiento.getFechaSalida();
            costo = mantenimiento.getCosto();
            pagado = mantenimiento.isPagado();
            notas = mantenimiento.getNotas();

        } else if (serviceObject instanceof Hojalateria) {
            Hojalateria hojalateria = (Hojalateria) serviceObject;
            serviceTypeName = "Servicio: Hojalatería"; // Nombre genérico
            if (hojalateria.getTiposTrabajo() != null && !hojalateria.getTiposTrabajo().isEmpty()) {
                serviceTypeName += " (" + String.join(", ", hojalateria.getTiposTrabajo()) + ")";
            }

            fechaIngreso = hojalateria.getFechaIngreso();
            fechaSalida = hojalateria.getFechaSalida();
            costo = hojalateria.getCosto();
            pagado = hojalateria.isPagado();
            notas = hojalateria.getNotas();

            // Mostrar campos específicos de Hojalatería
            layoutHojalateriaDetails.setVisibility(View.VISIBLE);
            if (hojalateria.getColorHex() != null) {
                try {
                    String colorString = hojalateria.getColorHex().startsWith("#") ? hojalateria.getColorHex() : "#" + hojalateria.getColorHex();
                    viewDetailColorPreview.setBackgroundColor(Color.parseColor(colorString));
                    tvDetailColorHex.setText(hojalateria.getColorHex());
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Color hex inválido para Hojalateria: " + hojalateria.getColorHex(), e);
                    viewDetailColorPreview.setBackgroundColor(Color.TRANSPARENT);
                    tvDetailColorHex.setText("N/A");
                }
            } else {
                viewDetailColorPreview.setBackgroundColor(Color.TRANSPARENT);
                tvDetailColorHex.setText("N/A");
            }
            if (hojalateria.getTiposTrabajo() != null && !hojalateria.getTiposTrabajo().isEmpty()) {
                tvDetailTiposTrabajo.setText("Trabajo(s): " + String.join(", ", hojalateria.getTiposTrabajo()));
            } else {
                tvDetailTiposTrabajo.setText("Trabajo(s): N/A");
            }

        } else {
            // Esto no debería pasar si los datos en Firebase son consistentes
            layoutServiceDetails.setVisibility(View.GONE);
            tvNoServiceInfo.setVisibility(View.VISIBLE);
            tvNoServiceInfo.setText("Tipo de servicio desconocido.");
            return;
        }

        tvDetailServiceType.setText(serviceTypeName);
        tvDetailFechaIngreso.setText(fechaIngreso != null ? dateFormatter.format(fechaIngreso) : "N/A");
        tvDetailFechaSalida.setText(fechaSalida != null ? dateFormatter.format(fechaSalida) : "N/A");
        tvDetailCosto.setText(currencyFormatter.format(costo));
        tvDetailEstadoPago.setText(pagado ? "Pagado" : "Pendiente");
        tvDetailEstadoPago.setBackgroundResource(pagado ? R.drawable.rounded_tag_active : R.drawable.rounded_tag_pending);

        if (notas != null && !notas.isEmpty()) {
            tvDetailNotas.setText(notas);
            tvDetailNotasLabel.setVisibility(View.VISIBLE);
            tvDetailNotas.setVisibility(View.VISIBLE);
        } else {
            tvDetailNotasLabel.setVisibility(View.GONE);
            tvDetailNotas.setVisibility(View.GONE);
        }
    }
}