package com.example.autolinkmanager.ui.SelecServices;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.Auto;
import com.example.autolinkmanager.Mantenimiento;
import com.example.autolinkmanager.R;
import com.example.autolinkmanager.ui.mantenimiento.MantenimientoFragment;
import com.google.android.material.card.MaterialCardView;

public class SectServices extends Fragment {
    private Auto auto;

    private TextView tvVehicleTitle;
    private LinearLayout cardMantenimiento;
    private LinearLayout cardHojalateria;

    public SectServices() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            auto = (Auto) getArguments().getSerializable("auto_data");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sect_services, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvVehicleTitle = view.findViewById(R.id.tv_vehicle_title);
        cardMantenimiento = view.findViewById(R.id.card_mantenimiento);
        cardHojalateria = view.findViewById(R.id.card_hojalateria);

        if (auto != null) {
            String title = auto.getPlaca() + " — " + auto.getModelo() + " " + auto.getAnio();
            tvVehicleTitle.setText(title);
        }

        // Navigate to Mantenimiento
        cardMantenimiento.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putSerializable("auto_data", auto);
            args.putString("SERVICE_TYPE", "Mantenimiento");
            NavController navController = Navigation.findNavController(v);
            navController.navigate(R.id.action_nav_select_serv_to_nav_mantenimiento, args);
        });

        // ***** NAVEGAR A HOJALATERÍA *****
        cardHojalateria.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putSerializable("auto_data", auto); // Pass the Auto object
            args.putString("SERVICE_TYPE", "Hojalatería");
            NavController navController = Navigation.findNavController(v);
            // Use the action ID from mobile_navigation.xml
            navController.navigate(R.id.action_nav_select_serv_to_nav_hojalateria, args);
        });
    }
}