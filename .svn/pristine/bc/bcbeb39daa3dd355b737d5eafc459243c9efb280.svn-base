package com.egogame.plugin.vehiclemodel.api;

/** 未知状态不等价于零；车辆属性只读，不在插件协议中提供真实车控。 */
public interface VehicleReadOnlyState {
    String CAPABILITY = "yudan.vehicle-state.read";
    interface Subscription extends AutoCloseable { @Override void close(); }
    interface Listener {
        void onState(String property, double value, String unit, long timestampMillis, boolean valid);
    }
    Subscription observe(String[] properties, Listener listener);
}
