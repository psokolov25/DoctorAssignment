package com.qsystems.meddoctorassignment.adapter.orchestra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO рабочего профиля Orchestra.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkProfileData {

    private int id;
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
