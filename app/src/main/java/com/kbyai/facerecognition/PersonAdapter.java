package com.kbyai.facerecognition;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton; // Changed to ImageButton
import android.widget.Toast;

import java.util.ArrayList;

public class PersonAdapter extends ArrayAdapter<Person> {

    DBManager dbManager;
    Context context;

    public PersonAdapter(Context context, ArrayList<Person> personList) {
        super(context, 0, personList);
        this.context = context;
        dbManager = new DBManager(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Person person = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_person, parent, false);
        }

        TextView tvName = convertView.findViewById(R.id.textName);
        ImageView faceView = convertView.findViewById(R.id.imageFace);
        // Ensure you're using an ImageButton if that's what your layout has
        ImageButton deleteButton = convertView.findViewById(R.id.buttonDelete); // Changed to ImageButton

        tvName.setText(person.name);
        faceView.setImageBitmap(person.face);

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(context)
                        .setTitle("Remove Person")
                        .setMessage("Are you sure you want to remove " + person.name + "?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            dbManager.deletePerson(person.name);
                            remove(person);  // remove from adapter
                            notifyDataSetChanged();
                            Toast.makeText(context, person.name + " deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        return convertView;
    }
}
