package it.univaq.masaccio.rest.business.repository;

import it.univaq.masaccio.rest.business.domain.Area;
import it.univaq.masaccio.rest.business.domain.Sensor;

import java.util.List;


public interface MasaccioService {


    // Sensor services

    List<Sensor> findAllSensorsByArea(Long id);

    List<Sensor> findAllSensors();

    Sensor findSensorById(Long id);

    // end Sensor services


    // Area services

    List<Area> findAllAreas();

    Area findAreaById(Long id);

    // end Area servies

}