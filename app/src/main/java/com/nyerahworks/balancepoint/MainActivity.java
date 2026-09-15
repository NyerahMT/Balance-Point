package com.nyerahworks.balancepoint;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public final class MainActivity extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useWakelock = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        config.useRotationVectorSensor = false;
        config.numSamples = 0;
        config.depth = 16;
        config.stencil = 0;

        initialize(new BalancePointGame(), config);
    }
}
