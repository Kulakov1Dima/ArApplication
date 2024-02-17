package com.example.iarsecuritysystem;

import androidx.appcompat.app.AppCompatActivity;


import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.common.helpers.CameraPermissionHelper;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkArcoreService();
    }

    private void checkArcoreService(){
        ArCoreApk.getInstance().checkAvailabilityAsync(this, availability -> {
            if(!availability.isSupported()) {
                errorSupportInfo(); // извещение пользователя об ошибке
            }
            else{
                try {
                    checkInstall();
                } catch (UnavailableDeviceNotCompatibleException |
                         UnavailableUserDeclinedInstallationException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void errorSupportInfo(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Это устройство не поддерживает AR")
                .setCancelable(false)
                .setPositiveButton("Закрыть", (dialog, id) -> finish())
                .setNegativeButton("Исправить", (dialog, id) -> {
                    launchArCoreInstallIntent();
                    dialog.dismiss();
                    finish();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void errorInstallInfo(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("На этом устройстве не учтановлены сервисы AR. Или попробуйте перезайти.")
                .setCancelable(false)
                .setPositiveButton("Закрыть", (dialog, id) -> finish())
                .setNegativeButton("Исправить", (dialog, id) -> {
                    launchArCoreInstallIntent();
                    dialog.dismiss();
                    finish();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void checkInstall() throws UnavailableDeviceNotCompatibleException, UnavailableUserDeclinedInstallationException {
        // Запрос установки ARCore
        ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(this,true);

        if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
            errorInstallInfo();
        }
        else{
            Intent myIntent = new Intent(this, ARFrame.class);
            startActivity(myIntent);
            finish();
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
        }
    }

    public void launchArCoreInstallIntent() {
        // Отправляем пользователя на Play market, чтобы он попытался включить сервисы AR
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.google.ar.core"));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core"));
            startActivity(intent);
        }
    }
}