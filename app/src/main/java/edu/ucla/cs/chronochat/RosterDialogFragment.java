package edu.ucla.cs.chronochat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.Arrays;


public class RosterDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        String[] roster = getArguments().getStringArray(ChronoChatService.EXTRA_ROSTER);
        if (roster != null)
            Arrays.sort(roster, String.CASE_INSENSITIVE_ORDER);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_roster)
                .setPositiveButton(R.string.dismiss_roster, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) { }
                })
                .setItems(roster, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { }
                });

        return builder.create();
    }
}
