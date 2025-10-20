package com.example.autolinkmanager;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.autolinkmanager.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private boolean isAdmin = false;

    private boolean initialNavDone = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);

        binding.appBarMain.fab.setOnClickListener(view ->
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show()
        );

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery,
                R.id.nav_create_agency, R.id.nav_agencies_map, R.id.nav_assign_agency,
                R.id.nav_manage_agencies, R.id.nav_add_car, R.id.nav_car_list
        ).setOpenableLayout(drawer).build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        navigationView.getMenu().findItem(R.id.nav_home).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_gallery).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_create_agency).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_agencies_map).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_assign_agency).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_manage_agencies).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_add_car).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_car_list).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_calendar).setVisible(false);

        showAdminItemsIfNeeded();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_logout) {
                logoutUser();
                drawer.closeDrawers();
                return true;
            }

            if (id == R.id.nav_about) {
                showAboutDialog();
                drawer.closeDrawers();
                return true;
            }

            if ((id == R.id.nav_create_agency || id == R.id.nav_agencies_map || id == R.id.nav_assign_agency || id == R.id.nav_manage_agencies) && !isAdmin) {
                Snackbar.make(binding.getRoot(), "Acceso solo para administradores", Snackbar.LENGTH_SHORT).show();
                drawer.closeDrawers();
                return true;
            }

            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled) drawer.closeDrawers();
            return handled;
        });
    }
    private void showAdminItemsIfNeeded() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        final NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    String role = snap.getString("role");
                    isAdmin = "admin".equalsIgnoreCase(role);

                    // Visibilidad de menús
                    boolean adminVisible = isAdmin;
                    binding.navView.getMenu().findItem(R.id.nav_gallery).setVisible(adminVisible);
                    binding.navView.getMenu().findItem(R.id.nav_create_agency).setVisible(adminVisible);
                    binding.navView.getMenu().findItem(R.id.nav_agencies_map).setVisible(adminVisible);
                    binding.navView.getMenu().findItem(R.id.nav_assign_agency).setVisible(adminVisible);
                    binding.navView.getMenu().findItem(R.id.nav_manage_agencies).setVisible(adminVisible);

                    boolean userVisible = !isAdmin;
                    binding.navView.getMenu().findItem(R.id.nav_home).setVisible(userVisible);
                    binding.navView.getMenu().findItem(R.id.nav_add_car).setVisible(userVisible);
                    binding.navView.getMenu().findItem(R.id.nav_car_list).setVisible(userVisible);
                    binding.navView.getMenu().findItem(R.id.nav_calendar).setVisible(userVisible);

                    // 👉 Navegación inicial por rol (una sola vez)
                    if (!initialNavDone) {
                        int target = isAdmin ? R.id.nav_gallery : R.id.nav_home;

                        // Evita navegar si ya estás en el destino
                        if (navController.getCurrentDestination() == null
                                || navController.getCurrentDestination().getId() != target) {

                            NavOptions opts = new NavOptions.Builder()
                                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true) // limpia backstack al start
                                    .setLaunchSingleTop(true)
                                    .build();

                            navController.navigate(target, null, opts);
                        }
                        initialNavDone = true;
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback: tratar como usuario-agencia
                    isAdmin = false;

                    binding.navView.getMenu().findItem(R.id.nav_gallery).setVisible(false);
                    binding.navView.getMenu().findItem(R.id.nav_create_agency).setVisible(false);
                    binding.navView.getMenu().findItem(R.id.nav_agencies_map).setVisible(false);
                    binding.navView.getMenu().findItem(R.id.nav_assign_agency).setVisible(false);
                    binding.navView.getMenu().findItem(R.id.nav_manage_agencies).setVisible(false);

                    binding.navView.getMenu().findItem(R.id.nav_home).setVisible(true);
                    binding.navView.getMenu().findItem(R.id.nav_add_car).setVisible(true);
                    binding.navView.getMenu().findItem(R.id.nav_car_list).setVisible(true);
                    binding.navView.getMenu().findItem(R.id.nav_calendar).setVisible(true);

                    if (!initialNavDone) {
                        NavOptions opts = new NavOptions.Builder()
                                .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                                .setLaunchSingleTop(true)
                                .build();
                        navController.navigate(R.id.nav_home, null, opts);
                        initialNavDone = true;
                    }
                });
    }



    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void showAboutDialog() {
        // Opción A: usar layout personalizado (dialog_about.xml)
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_about, null, false);

        // (Opcional) completar versión dinámica y permisos reales
        String versionName = "1.0";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        // Si quieres poner la versión en el título del layout, puedes buscar una TextView y setearla.
        // TextView tvVersion = content.findViewById(R.id.tvVersion);  // si la agregas
        // tvVersion.setText("Versión " + versionName);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Acerca de")
                .setView(content)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .show();

        // --- Opción B (si NO quieres layout, descomenta y usa esto en su lugar) ---
    /*
    String msg =
        "AutoLink Manager\n" +
        "Versión " + versionName + "\n\n" +
        "Desarrolladores:\n" +
        "• Elian Pérez\n" +
        "• Eduardo De Rosas\n" +
        "Carrera: ISW\n" +
        "Profesora: Rocío Pulido\n\n" +
        "Permisos utilizados:\n" +
        "- INTERNET\n- ACCESS_NETWORK_STATE\n- ACCESS_FINE_LOCATION\n- ACCESS_COARSE_LOCATION\n- WRITE_EXTERNAL_STORAGE (solo <= Android 9)";
    new MaterialAlertDialogBuilder(this)
        .setIcon(R.drawable.ic_info)
        .setTitle("Acerca de")
        .setMessage(msg)
        .setPositiveButton("OK", (d, w) -> d.dismiss())
        .show();
    */
    }
}