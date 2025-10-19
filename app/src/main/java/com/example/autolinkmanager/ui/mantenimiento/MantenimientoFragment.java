package com.example.autolinkmanager.ui.mantenimiento;

import androidx.lifecycle.ViewModelProvider;

import android.app.DatePickerDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.Auto;
import com.example.autolinkmanager.Mantenimiento;
import com.example.autolinkmanager.R;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MantenimientoFragment extends Fragment {
    private static final String TAG = "MantenimientoFragment";

    // Datos recibidos
    private String agencyId, serviceType;
    private Auto auto; // El objeto Auto de las pantallas anteriores

    // Vistas
    private Spinner tilTipoMantenimiento;
    private EditText tilFechaIngreso, tilFechaSalida, tilCosto, tilNotas;
    private TextView tvTipoError, tvFechaError;
    private RadioGroup rgEstadoPago;
    private RadioButton rbPagado;
    private Button btnGuardar, btnCancelar;

    private FirebaseFirestore db;
    private SimpleDateFormat dateFormatter;

    public MantenimientoFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Gracias al <argument> en el XML, esto ya no crashea
        if (getArguments() != null) {
            serviceType = getArguments().getString("SERVICE_TYPE");
            auto = (Auto) getArguments().getSerializable("auto_data");
            if (auto != null) {
                agencyId = auto.getAgencyId();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mantenimiento, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilTipoMantenimiento = view.findViewById(R.id.til_tipo_mantenimiento);
        tilFechaIngreso = view.findViewById(R.id.til_fecha_ingreso);
        tilFechaSalida = view.findViewById(R.id.til_fecha_salida);
        tilCosto = view.findViewById(R.id.til_costo);
        tilNotas = view.findViewById(R.id.til_notas);
        tvTipoError = view.findViewById(R.id.tv_tipo_error);
        tvFechaError = view.findViewById(R.id.tv_fecha_error);
        rgEstadoPago = view.findViewById(R.id.rg_estado_pago);
        rbPagado = view.findViewById(R.id.rb_pagado);
        btnGuardar = view.findViewById(R.id.btn_guardar);
        btnCancelar = view.findViewById(R.id.btn_cancelar);

        // --- Configurar Spinner ---
        String[] tipos = {"Preventivo", "Correctivo", "Revisión General"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                tipos);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tilTipoMantenimiento.setAdapter(adapter);

        // --- Configurar DatePickers ---
        tilFechaIngreso.setOnClickListener(v -> showDatePickerDialog(tilFechaIngreso));
        tilFechaSalida.setOnClickListener(v -> showDatePickerDialog(tilFechaSalida));

        btnGuardar.setOnClickListener(v -> guardarDatosEnFirebase());
        btnCancelar.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void showDatePickerDialog(final EditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (v, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);
                    editText.setText(dateFormatter.format(selectedDate.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void guardarDatosEnFirebase() {
        if (auto == null || agencyId == null) {
            Toast.makeText(getContext(), "Error: Faltan datos del auto o agencia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. OBTENER DATOS DE MANTENIMIENTO
        String tipoMantenimiento = tilTipoMantenimiento.getSelectedItem().toString();
        String fechaIngresoStr = tilFechaIngreso.getText().toString().trim();
        String fechaSalidaStr = tilFechaSalida.getText().toString().trim();
        String costoStr = tilCosto.getText().toString().trim();
        String notas = tilNotas.getText().toString().trim();
        boolean isPagado = rbPagado.isChecked();

        // --- Validación Manual ---
        boolean esValido = true;
        tvTipoError.setVisibility(View.GONE);
        tvFechaError.setVisibility(View.GONE);
        tilCosto.setError(null);

        if (tipoMantenimiento.isEmpty()) {
            tvTipoError.setVisibility(View.VISIBLE);
            esValido = false;
        }
        if (fechaIngresoStr.isEmpty() || fechaIngresoStr.equals("dd/mm/aaaa")) {
            tvFechaError.setVisibility(View.VISIBLE);
            esValido = false;
        }
        if (!esValido) return;

        Date fechaIngreso = null;
        Date fechaSalida = null;
        try {
            fechaIngreso = dateFormatter.parse(fechaIngresoStr);
            if (!fechaSalidaStr.isEmpty() && !fechaSalidaStr.equals("dd/mm/aaaa")) {
                fechaSalida = dateFormatter.parse(fechaSalidaStr);
            }
        } catch (ParseException e) {
            tvFechaError.setText("Formato de fecha incorrecto");
            tvFechaError.setVisibility(View.VISIBLE);
            return;
        }

        double costo = 0.0;
        if (!costoStr.isEmpty()) {
            try {
                costo = Double.parseDouble(costoStr);
            } catch (NumberFormatException e) {
                tilCosto.setError("Costo inválido");
                return;
            }
        }

        // 2. CREAR EL OBJETO 'Mantenimiento' COMPLETO
        Mantenimiento mantenimiento = new Mantenimiento();

        // --- Copiar datos del Auto ---
        mantenimiento.setPlaca(auto.getPlaca());
        mantenimiento.setModelo(auto.getModelo());
        mantenimiento.setAnio(auto.getAnio());
        mantenimiento.setNombrePropietario(auto.getNombrePropietario());
        mantenimiento.setTelefonoPropietario(auto.getTelefonoPropietario());
        mantenimiento.setFotoBase64(auto.getFotoBase64());
        mantenimiento.setAgencyId(auto.getAgencyId()); // Guarda el ID de la agencia

        // --- Añadir datos del Mantenimiento ---
        mantenimiento.setTipoMantenimiento(tipoMantenimiento);
        mantenimiento.setFechaIngreso(fechaIngreso);
        mantenimiento.setFechaSalida(fechaSalida);
        mantenimiento.setCosto(costo);
        mantenimiento.setPagado(isPagado);
        mantenimiento.setNotas(notas);

        // 3. GUARDAR EL OBJETO COMPLETO EN FIREBASE
        Toast.makeText(getContext(), "Guardando...", Toast.LENGTH_SHORT).show();

        db.collection("agencies").document(agencyId)
                .collection("vehicles")
                .add(mantenimiento)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "¡Vehículo y Servicio guardados!", Toast.LENGTH_SHORT).show();
                    // Regresa al inicio (nav_home)
                    Navigation.findNavController(requireView()).popBackStack(R.id.nav_home, false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error al guardar en Firebase", e);
                });
    }
}