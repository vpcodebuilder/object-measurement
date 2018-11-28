package com.vpcodebuilder.objectmeasurement;

import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class TabsPagerAdapter extends FragmentPagerAdapter {
	ArrayList<String> tabs;

    public TabsPagerAdapter(FragmentManager fm , ArrayList<String> tabs) {
        super(fm);
        this.tabs = tabs;
    }

    @Override
    public Fragment getItem(int position) {
    	TabCaptureFragment oTabCaptureFragment = new TabCaptureFragment();
    	Bundle data = new Bundle();
    	
    	data.putInt("position", position);
    	oTabCaptureFragment.setArguments(data);
    	
    	return oTabCaptureFragment;
    }

    @Override
    public int getCount() {
        return tabs.size();
    }
}