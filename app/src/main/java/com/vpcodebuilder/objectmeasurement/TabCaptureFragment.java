package com.vpcodebuilder.objectmeasurement;

import java.util.Locale;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TabCaptureFragment extends Fragment {
    public TextView lblObjectMeterWidthValue;
    public TextView lblObjectMeterHeightValue;
    public ImageView imgResult;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout oLinearLayout = (LinearLayout)inflater.inflate(R.layout.fragment_tabcapture, container, false);
        
        lblObjectMeterWidthValue = (TextView)oLinearLayout.findViewById(R.id.lblObjectMeterWidthValue);
        lblObjectMeterHeightValue = (TextView)oLinearLayout.findViewById(R.id.lblObjectMeterHeightValue);
        imgResult = (ImageView)oLinearLayout.findViewById(R.id.imgResult);
        
        MainActivity mainActivity = (MainActivity)this.getActivity();
        Bundle data = this.getArguments();
        int position = data.getInt("position");
        DBObjectMeasurement oDBObjectMeasurement = mainActivity.getData(position);

        if (oDBObjectMeasurement != null) {
            lblObjectMeterWidthValue.setText(String.format(Locale.ENGLISH, "%.2f m.", oDBObjectMeasurement.getObjectMeterWidth()));
            lblObjectMeterHeightValue.setText(String.format(Locale.ENGLISH, "%.2f m.", oDBObjectMeasurement.getObjectMeterHeight()));
            imgResult.setImageBitmap(oDBObjectMeasurement.getImageData());
        }

        return oLinearLayout;
    }
}