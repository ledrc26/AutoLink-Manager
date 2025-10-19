package com.example.autolinkmanager.ui.addauto;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autolinkmanager.Auto;
import com.example.autolinkmanager.R;
import com.example.autolinkmanager.User;
import com.example.autolinkmanager.ui.SelecServices.SectServices;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;


public class AddAutoFragment extends Fragment {
    private static final String TAG = "AddAutoFragment";

    // Vistas
    private EditText tilPlaca, tilModelo, tilAnio, tilNombre, tilTelefono;
    private TextView tvPlacaError;
    private Button btnSiguiente, btnCancelar, btnTomarFoto, btnGaleria;
    private ImageView ivFotoPreview;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentAgencyId;

    // Foto
    private String fotoBase64 = null;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    public AddAutoFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- INICIALIZAR LAUNCHERS ---

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            Bitmap imageBitmap = (Bitmap) extras.get("data");
                            if (imageBitmap != null) {
                                ivFotoPreview.setImageBitmap(imageBitmap);
                                fotoBase64 = bitmapToBase64(imageBitmap);
                            }
                        }
                    }
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            ivFotoPreview.setImageBitmap(bitmap);
                            fotoBase64 = bitmapToBase64(bitmap);
                        } catch (Exception e) {
                            Log.e(TAG, "Error al cargar imagen de galería", e);
                        }
                    }
                });

        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraLauncher.launch(takePictureIntent);
                    } else {
                        Toast.makeText(getContext(), "Permiso de cámara denegado.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_auto, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tilPlaca = view.findViewById(R.id.til_placa);
        tilModelo = view.findViewById(R.id.til_modelo);
        tilAnio = view.findViewById(R.id.til_anio);
        tilNombre = view.findViewById(R.id.til_nombre);
        tilTelefono = view.findViewById(R.id.til_telefono);
        tvPlacaError = view.findViewById(R.id.tv_placa_error);
        ivFotoPreview = view.findViewById(R.id.iv_foto_preview);
        btnSiguiente = view.findViewById(R.id.btn_siguiente);
        btnCancelar = view.findViewById(R.id.btn_cancelar);
        btnTomarFoto = view.findViewById(R.id.btn_tomar_foto);
        btnGaleria = view.findViewById(R.id.btn_galeria);

        loadUserAgencyId();

        btnTomarFoto.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        btnGaleria.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnSiguiente.setOnClickListener(v -> irAServicios());
        btnCancelar.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
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
                                } else {
                                    Toast.makeText(getContext(), "Usuario sin ID de agencia.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), "Documento de usuario no encontrado.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Error al cargar usuario", task.getException());
                        }
                    });
        }
    }

    private void irAServicios() {
        if (currentAgencyId == null) {
            Toast.makeText(getContext(), "ID de agencia no cargado. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
            loadUserAgencyId();
            return;
        }

        String placa = tilPlaca.getText().toString().trim();
        String modelo = tilModelo.getText().toString().trim();
        String anioStr = tilAnio.getText().toString().trim();
        String nombre = tilNombre.getText().toString().trim();
        String telefono = tilTelefono.getText().toString().trim();

        boolean esValido = true;
        tvPlacaError.setVisibility(View.GONE);
        tilPlaca.setError(null);
        tilModelo.setError(null);
        tilAnio.setError(null);

        if (placa.isEmpty()) {
            tilPlaca.setError("Campo requerido");
            esValido = false;
        }
        if (modelo.isEmpty()) {
            tilModelo.setError("Campo requerido");
            esValido = false;
        }
        if (anioStr.isEmpty()) {
            tilAnio.setError("Campo requerido");
            esValido = false;
        }
        if (fotoBase64 == null) {
            Toast.makeText(getContext(), "Debe añadir una foto.", Toast.LENGTH_LONG).show();
            esValido = false;
        }
        if (!esValido) {
            return;
        }

        int anio = Integer.parseInt(anioStr);

        // 1. CREAR EL OBJETO AUTO
        Auto auto = new Auto();
        auto.setPlaca(placa);
        auto.setModelo(modelo);
        auto.setAnio(anio);
        auto.setNombrePropietario(nombre);
        auto.setTelefonoPropietario(telefono);
        auto.setFotoBase64(fotoBase64);
        auto.setAgencyId(currentAgencyId);

        // 2. PREPARAR DATOS PARA EL SIGUIENTE FRAGMENT
        Bundle args = new Bundle();
        args.putSerializable("auto_data", auto);

        // 3. NAVEGAR
        NavController navController = Navigation.findNavController(requireView());
        // Usa el ID de la acción del XML
        navController.navigate(R.id.action_nav_add_car_to_nav_select_serv, args);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
}