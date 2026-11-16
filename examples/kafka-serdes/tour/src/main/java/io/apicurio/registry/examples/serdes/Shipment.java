package io.apicurio.registry.examples.serdes;

import java.util.Objects;

/** A plain Java bean, serialized to JSON and validated against shipment.json (tour step 5). */
public class Shipment {

    private String shipmentId;
    private String orderId;
    private int parcels;

    public Shipment() {
    }

    public Shipment(String shipmentId, String orderId, int parcels) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.parcels = parcels;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public int getParcels() {
        return parcels;
    }

    public void setParcels(int parcels) {
        this.parcels = parcels;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Shipment s && parcels == s.parcels && Objects.equals(shipmentId, s.shipmentId)
                && Objects.equals(orderId, s.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipmentId, orderId, parcels);
    }

    @Override
    public String toString() {
        return "Shipment[" + shipmentId + ", " + orderId + ", " + parcels + "]";
    }
}
