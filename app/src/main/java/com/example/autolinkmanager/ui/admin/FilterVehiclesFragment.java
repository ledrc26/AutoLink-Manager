package com.example.autolinkmanager.ui.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.autolinkmanager.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FilterVehiclesFragment extends Fragment {

    // UI
    private Spinner spAgency;
    private Button btnPickDate, btnFilter;
    private RadioGroup rgRange;
    private RadioButton rbDay, rbWeek, rbMonth;
    private TextView tvPickedDate, tvSummary, tvEmpty;
    private ProgressBar progress;
    private ListView lvVehicles;

    // Firebase
    private FirebaseFirestore db;

    // Agencias
    private final List<String> agencyIds = new ArrayList<>();
    private final List<String> agencyLabels = new ArrayList<>();
    private ArrayAdapter<String> agenciesAdapter;

    // Lista de vehículos
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    // Fecha base
    private final Calendar baseCal = Calendar.getInstance();
    private final SimpleDateFormat sdfDay  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public FilterVehiclesFragment() {
        // Constructor vacío requerido
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_filter_vehicles, container, false);

        spAgency     = v.findViewById(R.id.spAgency);
        btnPickDate  = v.findViewById(R.id.btnPickDate);
        btnFilter    = v.findViewById(R.id.btnFilter);
        rgRange      = v.findViewById(R.id.rgRange);
        rbDay        = v.findViewById(R.id.rbDay);
        rbWeek       = v.findViewById(R.id.rbWeek);
        rbMonth      = v.findViewById(R.id.rbMonth);
        tvPickedDate = v.findViewById(R.id.tvPickedDate);
        tvSummary    = v.findViewById(R.id.tvSummary);
        tvEmpty      = v.findViewById(R.id.tvEmpty);
        progress     = v.findViewById(R.id.progress);
        lvVehicles   = v.findViewById(R.id.lvVehicles);

        db = FirebaseFirestore.getInstance();

        // ✅ Resetear la hora al inicio del día desde el inicio
        setStartOfDay(baseCal);

        // Spinner de agencias
        agenciesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                agencyLabels
        );
        spAgency.setAdapter(agenciesAdapter);

        // Adaptador de lista (usamos un layout propio en getView)
        listAdapter = new ArrayAdapter<String>(
                requireContext(),
                android.R.layout.simple_list_item_1
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View rowView = getLayoutInflater().inflate(R.layout.item_vehicle_row, parent, false);

                TextView tvTitle    = rowView.findViewById(R.id.tvTitle);
                TextView tvSubtitle = rowView.findViewById(R.id.tvSubtitle);
                TextView tvStatus   = rowView.findViewById(R.id.tvStatus);

                Map<String, Object> m = rows.get(position);

                String placa  = value(m.get("placa"));
                String modelo = value(m.get("modelo"));

                Double costo  = m.get("costo") instanceof Number
                        ? ((Number) m.get("costo")).doubleValue()
                        : null;

                Boolean pagado    = (Boolean) m.get("pagado");
                Boolean terminado = (Boolean) m.get("isFinished");
                Timestamp t       = (Timestamp) m.get("fechaIngreso");

                // Título: Placa — Modelo
                tvTitle.setText(
                        (placa.isEmpty() ? "—" : placa) +
                                " — " +
                                (modelo.isEmpty() ? "—" : modelo)
                );

                // Ingreso solo con FECHA (ya sin hora)
                String ing = (t != null) ? sdfDay.format(t.toDate()) : "—";

                // Costo
                String cos = (costo != null)
                        ? String.format(Locale.getDefault(), "$%.2f", costo)
                        : "—";

                // Pagado
                String pay = (pagado != null && pagado) ? "Sí" : "No";

                // Subtítulo: Ingreso + Costo + Pagado
                tvSubtitle.setText(
                        "Ingreso: " + ing +
                                "  •  Costo: " + cos +
                                "  •  Pagado: " + pay
                );

                // Estado Terminado/Pendiente con COLOR
                if (terminado != null && terminado) {
                    tvStatus.setText("Terminado");
                    tvStatus.setTextColor(0xFF4CAF50); // Verde
                } else {
                    tvStatus.setText("En proceso");
                    tvStatus.setTextColor(0xFFF44336); // Rojo
                }

                return rowView;
            }

        };
        lvVehicles.setAdapter(listAdapter);

        // Fecha por defecto = hoy
        tvPickedDate.setText(sdfDay.format(baseCal.getTime()));

        btnPickDate.setOnClickListener(view -> openDatePicker());
        btnFilter.setOnClickListener(view -> doFilter());

        loadAgencies();

        return v;
    }

    private void openDatePicker() {
        int y = baseCal.get(Calendar.YEAR);
        int m = baseCal.get(Calendar.MONTH);
        int d = baseCal.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            baseCal.set(Calendar.YEAR, year);
            baseCal.set(Calendar.MONTH, month);
            baseCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            setStartOfDay(baseCal);
            tvPickedDate.setText(sdfDay.format(baseCal.getTime()));
        }, y, m, d).show();
    }

    private void loadAgencies() {
        progress.setVisibility(View.VISIBLE);
        db.collection("agencies")
                .orderBy("name")
                .get()
                .addOnSuccessListener(snap -> {
                    agencyIds.clear();
                    agencyLabels.clear();
                    for (DocumentSnapshot d : snap) {
                        agencyIds.add(d.getId());
                        String name = d.getString("name");
                        agencyLabels.add(name != null ? name : d.getId());
                    }
                    agenciesAdapter.notifyDataSetChanged();
                    progress.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error cargando agencias: " + e.getMessage());
                });
    }

    private void doFilter() {
        int pos = spAgency.getSelectedItemPosition();
        if (pos < 0 || pos >= agencyIds.size()) {
            toast("Selecciona una agencia");
            return;
        }
        String agencyId = agencyIds.get(pos);

        // ✅ Calcula [start, end) según rango
        Calendar start = Calendar.getInstance();
        start.setTime(baseCal.getTime());
        setStartOfDay(start);

        Calendar end = Calendar.getInstance();
        end.setTime(baseCal.getTime());
        setStartOfDay(end);

        if (rbDay.isChecked()) {
            // Solo el día seleccionado
            end.add(Calendar.DAY_OF_MONTH, 1);

        } else if (rbWeek.isChecked()) {
            // ✅ Toda la semana (Lunes a Domingo)
            // Ir al inicio de la semana (lunes)
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            // Ir al final de la semana (domingo 23:59:59)
            end.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            end.add(Calendar.WEEK_OF_YEAR, 1);

        } else { // Mes
            // ✅ Todo el mes (día 1 al último día)
            // Ir al primer día del mes
            start.set(Calendar.DAY_OF_MONTH, 1);
            // Ir al primer día del mes siguiente
            end.set(Calendar.DAY_OF_MONTH, 1);
            end.add(Calendar.MONTH, 1);
        }

        Timestamp tsStart = new Timestamp(new Date(start.getTimeInMillis()));
        Timestamp tsEnd   = new Timestamp(new Date(end.getTimeInMillis()));

        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        db.collection("agencies")
                .document(agencyId)
                .collection("vehicles")
                .whereGreaterThanOrEqualTo("fechaIngreso", tsStart)
                .whereLessThan("fechaIngreso", tsEnd)
                .orderBy("fechaIngreso", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    rows.clear();
                    double total = 0;
                    int pendientes = 0;

                    for (DocumentSnapshot d : snap) {
                        Map<String, Object> m = d.getData();
                        if (m == null) continue;
                        rows.add(m);

                        Double costo = d.getDouble("costo");
                        if (costo != null) total += costo;
                        Boolean pagado = d.getBoolean("pagado");
                        if (pagado == null || !pagado) pendientes++;
                    }

                    tvSummary.setText(
                            "Vehículos: " + rows.size() +
                                    "   •   Monto total: $" +
                                    String.format(Locale.getDefault(), "%.2f", total) +
                                    "   •   Pendientes: " + pendientes
                    );

                    listAdapter.clear();
                    // solo para que el adapter tenga mismo tamaño que rows:
                    for (int i = 0; i < rows.size(); i++) listAdapter.add("");
                    listAdapter.notifyDataSetChanged();

                    progress.setVisibility(View.GONE);
                    tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    toast("Error consultando vehículos: " + e.getMessage());
                });
    }

    private void setStartOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private String value(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}