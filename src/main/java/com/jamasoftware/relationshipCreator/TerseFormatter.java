package com.jamasoftware.relationshipCreator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Modify the log formatter to output more useful levels
 */
public class TerseFormatter extends Formatter {
    private boolean firstRecord = true;
    public TerseFormatter() { super(); }

    @Override
    public String format(final LogRecord record) {
        String prepend = "\r\n";
        String level = record.getLevel().toString();
        if(firstRecord) {
            prepend = "";
            firstRecord = false;
        }
        if(level.equals("SEVERE")) {
            prepend += "ERROR";
        } else if (level.equals("INFO")) {
            prepend += "NEW";
        } else if (level.equals("WARNING")) {
            prepend += "ALERT";
        } else {
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
            String formattedDate = sdf.format(date);
            prepend += formattedDate + "\r\nFINISHED";
        }
        return prepend + ": " + record.getMessage().trim();
    }
}
