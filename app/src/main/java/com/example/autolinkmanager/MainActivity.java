package com.example.autolinkmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
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

    private boolean isAdmin = false; //

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
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow,
                R.id.nav_create_agency, R.id.nav_agencies_map
        ).setOpenableLayout(drawer).build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        navigationView.getMenu().findItem(R.id.nav_create_agency).setVisible(false);
        navigationView.getMenu().findItem(R.id.nav_agencies_map).setVisible(false);

        showAdminItemsIfNeeded();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_logout) {
                logoutUser();
                drawer.closeDrawers();
                return true;
            }

            if ((id == R.id.nav_create_agency || id == R.id.nav_agencies_map) && !isAdmin) {
                Snackbar.make(binding.getRoot(), "Acceso solo para administradores", Snackbar.LENGTH_SHORT).show();
                drawer.closeDrawers();
                return true; // consumimos el click, no navegamos
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

        db.collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    String role = snap.getString("role");
                    isAdmin = "admin".equalsIgnoreCase(role);

                    // Mostrar/ocultar items de admin
                    binding.navView.getMenu().findItem(R.id.nav_create_agency).setVisible(isAdmin);
                    binding.navView.getMenu().findItem(R.id.nav_agencies_map).setVisible(isAdmin);
                })
                .addOnFailureListener(e -> {
                    isAdmin = false;
                    binding.navView.getMenu().findItem(R.id.nav_create_agency).setVisible(false);
                    binding.navView.getMenu().findItem(R.id.nav_agencies_map).setVisible(false);
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
}
