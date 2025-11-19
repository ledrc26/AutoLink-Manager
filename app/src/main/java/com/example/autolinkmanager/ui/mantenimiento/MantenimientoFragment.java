package com.example.autolinkmanager.ui.mantenimiento;

import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
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
import com.google.firebase.firestore.DocumentSnapshot;
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
    private TextView tvFechaIngreso;
    private EditText  tilFechaSalida, tilCosto, tilNotas;
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
        tvFechaIngreso = view.findViewById(R.id.tv_fecha_ingreso_hojalateria);
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

        // --- CAMBIO 1: Poner la fecha actual automáticamente ---
        String fechaActual = dateFormatter.format(new Date());
        tvFechaIngreso.setText(fechaActual);

        // --- Configurar DatePickers ---
        // --- CAMBIO 2: Se elimina el OnClickListener para la fecha de ingreso ---
        // tilFechaIngreso.setOnClickListener(v -> showDatePickerDialog(tilFechaIngreso));
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

        // 1. OBTENER DATOS
        String tipoMantenimiento = tilTipoMantenimiento.getSelectedItem().toString();
        String fechaIngresoStr = tvFechaIngreso.getText().toString().trim();
        String fechaSalidaStr = tilFechaSalida.getText().toString().trim();
        String costoStr = tilCosto.getText().toString().trim();
        String notas = tilNotas.getText().toString().trim();
        boolean isPagado = rbPagado.isChecked();

        // --- Validación Manual ---
        boolean esValido = true;
        tvTipoError.setVisibility(View.GONE);
        tvFechaError.setVisibility(View.GONE);
        tilCosto.setError(null);
        tilFechaSalida.setError(null); // Limpiar error previo

        if (tipoMantenimiento.isEmpty()) {
            tvTipoError.setVisibility(View.VISIBLE);
            esValido = false;
        }

        // Validar Fecha Ingreso
        if (fechaIngresoStr.isEmpty()) {
            tvFechaError.setVisibility(View.VISIBLE);
            esValido = false;
        }

        // --- CAMBIO: Validar Fecha Salida OBLIGATORIA ---
        if (fechaSalidaStr.isEmpty() || fechaSalidaStr.equals("dd/mm/aaaa")) {
            tilFechaSalida.setError("La fecha de salida es obligatoria");
            esValido = false;
        }

        if (!esValido) return;

        Date fechaIngresoTemp = null;
        Date fechaSalidaTemp = null;
        try {
            fechaIngresoTemp = dateFormatter.parse(fechaIngresoStr);
            // Como ya validamos que no sea vacía, parseamos directamente
            fechaSalidaTemp = dateFormatter.parse(fechaSalidaStr);

            // Opcional: Validar que la fecha de salida no sea anterior a la de ingreso
            if (fechaSalidaTemp.before(fechaIngresoTemp)) {
                tilFechaSalida.setError("La salida no puede ser antes del ingreso");
                return;
            }

        } catch (ParseException e) {
            tvFechaError.setText("Formato de fecha incorrecto");
            tvFechaError.setVisibility(View.VISIBLE);
            return;
        }

        double costoTemp = 0.0;
        if (!costoStr.isEmpty()) {
            try {
                costoTemp = Double.parseDouble(costoStr);
            } catch (NumberFormatException e) {
                tilCosto.setError("Costo inválido");
                return;
            }
        }

        // Variables finales para usar en la lambda
        final Date fechaIngreso = fechaIngresoTemp;
        final Date fechaSalida = fechaSalidaTemp;
        final double costo = costoTemp;

        // ------------------------------------------------------------------
        // VERIFICAR SI EL AUTO YA TIENE UN SERVICIO ACTIVO
        // ------------------------------------------------------------------
        Toast.makeText(getContext(), "Verificando disponibilidad...", Toast.LENGTH_SHORT).show();

// 1. Limpiamos la placa para evitar errores de espacios o minúsculas
        String placaConsulta = auto.getPlaca().trim();

        db.collection("agencies").document(agencyId)
                .collection("vehicles")
                .whereEqualTo("placa", placaConsulta) // Usamos la placa limpia
                .whereEqualTo("isFinished", false)    // Ahora coincidirá gracias al @PropertyName
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // SI ENTRA AQUÍ, SIGNIFICA QUE YA HAY UN SERVICIO ABIERTO
                        // Obtenemos el documento para ver qué tipo de servicio es (opcional)
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        String tipoExistente = doc.contains("tipoMantenimiento") ? "Mantenimiento" : "Hojalatería";

                        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                        builder.setTitle("⚠️ Vehículo Ocupado");
                        builder.setMessage("El auto con placas " + placaConsulta + " ya tiene un servicio de " + tipoExistente + " en curso y no ha sido finalizado.\n\nDebes terminar el servicio anterior para registrar uno nuevo.");
                        builder.setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss());
                        builder.show();

                    } else {
                        // NO HAY SERVICIO ACTIVO -> PROCEDEMOS A GUARDAR
                        guardarRegistro(tipoMantenimiento, fechaIngreso, fechaSalida, costo, isPagado, notas);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al verificar base de datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error query: ", e);
                });
    }

    // (El método guardarRegistro se mantiene igual que en la respuesta anterior)
    private void guardarRegistro(String tipo, Date fIngreso, Date fSalida, double costo, boolean pagado, String notas) {
        Mantenimiento mantenimiento = new Mantenimiento();

        // --- Copiar datos del Auto ---
        mantenimiento.setPlaca(auto.getPlaca());
        mantenimiento.setModelo(auto.getModelo());
        mantenimiento.setAnio(auto.getAnio());
        mantenimiento.setNombrePropietario(auto.getNombrePropietario());
        mantenimiento.setTelefonoPropietario(auto.getTelefonoPropietario());
        mantenimiento.setFotoBase64(auto.getFotoBase64());
        mantenimiento.setAgencyId(auto.getAgencyId());

        // --- Añadir datos del Mantenimiento ---
        mantenimiento.setTipoMantenimiento(tipo);
        mantenimiento.setFechaIngreso(fIngreso);
        mantenimiento.setFechaSalida(fSalida);
        mantenimiento.setCosto(costo);
        mantenimiento.setPagado(pagado);
        mantenimiento.setNotas(notas);

        // --- NUEVOS CAMPOS REQUERIDOS ---
        mantenimiento.setFinished(false); // Por defecto FALSE (servicio activo)
        mantenimiento.setFotoTerminadoBase64(null); // Por defecto NULL

        // Guardar
        db.collection("agencies").document(agencyId)
                .collection("vehicles")
                .add(mantenimiento)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "¡Servicio de Mantenimiento registrado!", Toast.LENGTH_SHORT).show();
                    if (getView() != null) {
                        Navigation.findNavController(requireView()).popBackStack(R.id.nav_home, false);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}