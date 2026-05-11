package com.ckos.ble;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class MyAdapter extends ArrayAdapter<Bluetooth> {
    private int resource_id;
    private LayoutInflater layoutInflater;

    public MyAdapter(Context context, int resource_id, List<Bluetooth> items) {
        super(context, resource_id, items);
        this.resource_id = resource_id;
        this.layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Bluetooth item = getItem(position);
        convertView = layoutInflater.inflate(R.layout.list_item, null);
        TextView name = (TextView)convertView.findViewById(R.id.name);
        TextView rssi = (TextView)convertView.findViewById(R.id.rssi);
        TextView address = (TextView)convertView.findViewById(R.id.address);
        name.setText(item.getName());
        rssi.setText(String.valueOf(item.getRssi()));
        address.setText(item.getAddress());
        return convertView;
    }
}
