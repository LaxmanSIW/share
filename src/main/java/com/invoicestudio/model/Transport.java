package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Transport {
    private String id;
    private String name = "";
    private String phone = "";
    private String vehicleNumber = "";
    private String createdAt;
    private String updatedAt;

    public Transport() {
        this.id = "trp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transport(String id, String name, String phone, String vehicleNumber) {
        this.id = (id != null && !id.isBlank()) ? id : "trp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.name = name != null ? name : "";
        this.phone = phone != null ? phone : "";
        this.vehicleNumber = vehicleNumber != null ? vehicleNumber : "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVehicleNumber() { return vehicleNumber != null ? vehicleNumber : ""; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getVehicleNo() { return getVehicleNumber(); }
    public void setVehicleNo(String vehicleNo) { setVehicleNumber(vehicleNo); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        if (vehicleNumber != null && !vehicleNumber.isBlank()) {
            return name + " (" + vehicleNumber + ")";
        }
        return name;
    }
}
