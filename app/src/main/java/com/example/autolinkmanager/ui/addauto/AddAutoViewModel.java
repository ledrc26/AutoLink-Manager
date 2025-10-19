package com.example.autolinkmanager.ui.addauto;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.autolinkmanager.Auto;

public class AddAutoViewModel extends ViewModel {

    private final MutableLiveData<Auto> mText;

    public AddAutoViewModel() {
        mText = new MutableLiveData<>();
    }

    public LiveData<Auto> getAuto() {
        return mText;
    }

    public void select(Auto auto) {
        mText.setValue(auto);
    }
}