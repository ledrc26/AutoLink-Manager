package com.example.autolinkmanager.ui.hojalateria;

import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.Auto;
import com.example.autolinkmanager.Hojalateria;
import com.example.autolinkmanager.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HojalateriaFragment extends Fragment {
    private static final String TAG = "HojalateriaFragment";

    // Received Data
    private String agencyId, serviceType;
    private Auto auto;

    // UI Views
    private ChipGroup chipGroupTipoHojalateria;
    private LinearLayout layoutColorPicker;
    private View viewColorPreview;
    private TextView tvColorHex;
    private EditText etFechaIngreso, etFechaSalida, etCosto, etNotas;
    private RadioGroup rgEstadoPago;
    private RadioButton rbNoPagado; // Need this for checking
    private Button btnGuardar, btnCancelar;
    // Add Buttons for camera/gallery if needed

    private FirebaseFirestore db;
    private SimpleDateFormat dateFormatter;
    private String selectedColorHex = "4CAF50"; // Default color

    public HojalateriaFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

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
        return inflater.inflate(R.layout.fragment_hojalateria, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Views
        chipGroupTipoHojalateria = view.findViewById(R.id.chipGroupTipoHojalateria);
        layoutColorPicker = view.findViewById(R.id.layout_color_picker);
        viewColorPreview = view.findViewById(R.id.view_color_preview);
        tvColorHex = view.findViewById(R.id.tv_color_hex);
        etFechaIngreso = view.findViewById(R.id.et_fecha_ingreso_hojalateria);
        etFechaSalida = view.findViewById(R.id.et_fecha_salida_hojalateria);
        etCosto = view.findViewById(R.id.et_costo_hojalateria);
        etNotas = view.findViewById(R.id.et_notas_hojalateria);
        rgEstadoPago = view.findViewById(R.id.rg_estado_pago_hojalateria);
        rbNoPagado = view.findViewById(R.id.rb_no_pagado_hojalateria); // Initialize this
        btnGuardar = view.findViewById(R.id.btn_guardar_hojalateria);
        btnCancelar = view.findViewById(R.id.btn_cancelar_hojalateria);
        // Initialize camera/gallery buttons if you have them

        // Set default color preview
        viewColorPreview.setBackgroundColor(Color.parseColor("#" + selectedColorHex));
        tvColorHex.setText(selectedColorHex);

        // --- Setup Listeners ---
        etFechaIngreso.setOnClickListener(v -> showDatePickerDialog(etFechaIngreso));
        etFechaSalida.setOnClickListener(v -> showDatePickerDialog(etFechaSalida));
        layoutColorPicker.setOnClickListener(v -> openColorPicker()); // Method to open a color picker dialog

        btnGuardar.setOnClickListener(v -> guardarDatosEnFirebase());
        btnCancelar.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        // Add listeners for camera/gallery buttons if needed
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

    private void openColorPicker() {
        // Define tus opciones de color (Hex string)
        final String[] colorHexOptions = {"FF0000", "00FF00", "0000FF", "FFFF00", "FF00FF", "00FFFF", "FFFFFF", "000000", "4CAF50"};
        final String[] colorNames = {"Rojo", "Verde", "Azul", "Amarillo", "Magenta", "Cyan", "Blanco", "Negro"}; // Nombres opcionales

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Selecciona un color");

        // Crea una lista simple para mostrar los colores
        builder.setItems(colorNames, (dialog, which) -> {
            // 'which' es el índice del color seleccionado
            selectedColorHex = colorHexOptions[which];

            // Actualiza la UI
            try {
                // Añade '#' si no lo tiene, parseColor lo necesita
                String colorString = selectedColorHex.startsWith("#") ? selectedColorHex : "#" + selectedColorHex;
                viewColorPreview.setBackgroundColor(Color.parseColor(colorString));
                tvColorHex.setText(selectedColorHex); // Muestra el hex sin '#'
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Color hex inválido: " + selectedColorHex, e);
                // Mantener el color anterior o poner uno por defecto
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void guardarDatosEnFirebase() {
        if (auto == null || agencyId == null) {
            Toast.makeText(getContext(), "Error: Faltan datos del auto o agencia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. GET DATA FROM FORM
        List<String> selectedChips = new ArrayList<>();
        for (int i = 0; i < chipGroupTipoHojalateria.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupTipoHojalateria.getChildAt(i);
            if (chip.isChecked()) {
                selectedChips.add(chip.getText().toString());
            }
        }

        String fechaIngresoStr = etFechaIngreso.getText().toString().trim();
        String fechaSalidaStr = etFechaSalida.getText().toString().trim();
        String costoStr = etCosto.getText().toString().trim();
        String notas = etNotas.getText().toString().trim();
        boolean isPagado = !rbNoPagado.isChecked(); // If 'No pagado' is NOT checked, then it's 'Pagado'

        // --- Validation ---
        if (selectedChips.isEmpty()) {
            Toast.makeText(getContext(), "Selecciona al menos un tipo de trabajo.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fechaIngresoStr.isEmpty() || fechaIngresoStr.equals("dd/mm/aaaa")) {
            Toast.makeText(getContext(), "Ingresa la fecha de ingreso.", Toast.LENGTH_SHORT).show();
            // Show error state on etFechaIngreso if needed
            return;
        }
        // Add more validation if needed (cost, etc.)

        Date fechaIngreso = null;
        Date fechaSalida = null;
        try {
            fechaIngreso = dateFormatter.parse(fechaIngresoStr);
            if (!fechaSalidaStr.isEmpty() && !fechaSalidaStr.equals("dd/mm/aaaa")) {
                fechaSalida = dateFormatter.parse(fechaSalidaStr);
            }
        } catch (ParseException e) {
            Toast.makeText(getContext(), "Formato de fecha incorrecto.", Toast.LENGTH_SHORT).show();
            return;
        }

        double costo = 0.0;
        if (!costoStr.isEmpty()) {
            try {
                costo = Double.parseDouble(costoStr);
            } catch (NumberFormatException e) {
                etCosto.setError("Costo inválido");
                return;
            }
        }

        // 2. CREATE THE COMPLETE Hojalateria OBJECT
        Hojalateria hojalateria = new Hojalateria();

        // --- Copy data from Auto ---
        hojalateria.setPlaca(auto.getPlaca());
        hojalateria.setModelo(auto.getModelo());
        hojalateria.setAnio(auto.getAnio());
        hojalateria.setNombrePropietario(auto.getNombrePropietario());
        hojalateria.setTelefonoPropietario(auto.getTelefonoPropietario());
        hojalateria.setFotoBase64(auto.getFotoBase64());
        hojalateria.setAgencyId(auto.getAgencyId());

        // --- Add Hojalateria specific data ---
        hojalateria.setTiposTrabajo(selectedChips);
        hojalateria.setColorHex(selectedColorHex); // From the color picker logic
        hojalateria.setFechaIngreso(fechaIngreso);
        hojalateria.setFechaSalida(fechaSalida);
        hojalateria.setCosto(costo);
        hojalateria.setPagado(isPagado);
        hojalateria.setNotas(notas);

        // 3. SAVE TO FIREBASE
        Toast.makeText(getContext(), "Guardando...", Toast.LENGTH_SHORT).show();

        db.collection("agencies").document(agencyId)
                .collection("vehicles") // Save the complete Hojalateria object
                .add(hojalateria)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "¡Servicio de Hojalatería guardado!", Toast.LENGTH_SHORT).show();
                    // Navigate back to home (replace R.id.nav_home with your home destination ID)
                    Navigation.findNavController(requireView()).popBackStack(R.id.nav_home, false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error saving Hojalateria to Firebase", e);
                });
    }
}