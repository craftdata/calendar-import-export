/**
 *  Copyright (C) 2013  Dominik Schürmann <dominik@dominikschuermann.de>
 *  Copyright (C) 2010-2011  Lukas Aichbauer
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.ical;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.component.VEvent;

import org.sufficientlysecure.ical.ui.dialogs.DialogTools;
import org.sufficientlysecure.ical.util.ProviderTools;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.provider.CalendarContract;
import android.util.Log;

@SuppressLint("NewApi")
public class InsertVEvents extends ProcessVEvent {
    private static final String TAG = InsertVEvents.class.getSimpleName();

    public InsertVEvents(Activity activity, Calendar calendar, int calendarId) {
        super(activity, calendar, calendarId);
    }

    @Override
    public void run(ProgressDialog dialog) {
        try {
            if (!DialogTools.decisionDialog(getActivity(), R.string.dialog_information_title,
                    R.string.dialog_insert_entries, R.drawable.icon)) {
                return;
            }
            boolean checkForDuplicates = DialogTools.decisionDialog(getActivity(),
                    R.string.dialog_information_title,
                    R.string.dialog_insert_search_for_duplicates, R.drawable.icon);

            List<Integer> reminders = new ArrayList<Integer>();

            while (DialogTools.decisionDialog(getActivity(), R.string.dialog_reminder_title,
                        R.string.dialog_reminder_message, R.drawable.icon)) {
                String time_in_minutes = DialogTools.questionDialog(getActivity(),
                        R.string.dialog_reminder_title,
                        R.string.dialog_reminder_minutes_message,
                        android.R.string.ok, "10", true,
                        R.drawable.icon, false);
                try {
                    if (time_in_minutes != null) {
                        if (!reminders.contains(Integer.parseInt(time_in_minutes))) {
                            reminders.add(Integer.parseInt(time_in_minutes));
                        }
                    }
                } catch (Exception exc) {
                    DialogTools.showInformationDialog(getActivity(), R.string.dialog_bug_title,
                            R.string.dialog_bug_minutes_parse, R.drawable.icon);
                }
            }

            setProgressMessage(R.string.progress_insert_entries);
            ComponentList vevents = getCalendar().getComponents(VEvent.VEVENT);

            dialog.setMax(vevents.size());
            ContentResolver resolver = getActivity().getContentResolver();
            int numIns = 0;
            int numDups = 0;
            for (Object event : vevents) {
                incrementProgress(1);

                ContentValues eventVals = VEventWrapper.resolve((VEvent) event, getCalendarId());
                if (reminders.size() > 0) {
                    eventVals.put(CalendarContract.Events.HAS_ALARM, 1);
                }

                Log.d(TAG, "eventVals: " + eventVals);

                if (checkForDuplicates && contains(eventVals)) {
                    Log.d(TAG, "Ignoring duplicate");
                    numDups++;
                    continue;
                }

                Uri uri = insertAndLog(resolver, CalendarContract.Events.CONTENT_URI,
                        eventVals, "Event");
                if (uri == null) {
                    continue;
                }

                numIns++;

                for (int time : reminders) {

                    int id = Integer.parseInt(uri.getLastPathSegment());

                    Log.d(TAG, "Inserting reminder for event with id: " + id);

                    ContentValues reminderVals = new ContentValues();
                    reminderVals.put(CalendarContract.Reminders.EVENT_ID, id);
                    reminderVals.put(CalendarContract.Reminders.MINUTES, time);
                    reminderVals.put(CalendarContract.Reminders.METHOD,
                                    CalendarContract.Reminders.METHOD_ALERT);

                    insertAndLog(resolver, CalendarContract.Reminders.CONTENT_URI, reminderVals,
                            "Reminder");
                }
            }

            Resources res = getActivity().getResources();
            String message = res.getQuantityString(R.plurals.dialog_entries_inserted, numIns, numIns)
                + "\n" + res.getQuantityString(R.plurals.dialog_found_duplicates, numDups, numDups);

            DialogTools.showInformationDialog(getActivity(), R.string.dialog_information_title,
                    message, R.drawable.icon);

        } catch (Exception exc) {
            Log.e(TAG, "InsertVEvents", exc);
            try {
                ProviderTools.writeException(Environment.getExternalStorageDirectory()
                        + File.separator + "ical_error.log", exc);
            } catch (Exception e) {

            }
            DialogTools.showInformationDialog(getActivity(), R.string.dialog_bug_title,
                    R.string.dialog_bug, R.drawable.icon);
        }
    }

    private Uri insertAndLog(ContentResolver resolver, Uri uri, ContentValues vals, String type) {
        Uri result = resolver.insert(uri, vals);
        if (result == null) {
            Log.d(TAG, "Could not insert " + type);
        } else {
            Log.d(TAG,  "Inserted " + type + ": " + result.toString());
        }
        return result;
    }
}
