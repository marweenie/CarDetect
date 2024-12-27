package com.example.javaapp;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("insert_vehicle_speed.php")
    Call<Void> insertVehicleSpeed(@Body Map<String, Object> vehicleSpeed);
}