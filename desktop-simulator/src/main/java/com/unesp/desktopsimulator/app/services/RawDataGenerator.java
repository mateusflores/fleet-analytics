package com.unesp.desktopsimulator.app.services;

import com.unesp.desktopsimulator.model.RawData;

public interface RawDataGenerator {

    RawData generateData();

    RawData generateDependentData(RawData previousData);

}
