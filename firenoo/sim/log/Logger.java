package firenoo.sim.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class Logger {

    private BufferedWriter writer;

    public Logger(OutputStream out) {
        try {
            if(out == null) {
                File file = new File(String.format("log.txt"));
                file.createNewFile();
                out = new FileOutputStream(file, true);
            }
            
            writer = new BufferedWriter(new OutputStreamWriter(out), 4096);
        } catch (IOException e) {
            writer = new BufferedWriter(new OutputStreamWriter(System.out), 4096);
        }
        log("Started logging.");
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    public void close() {
        synchronized(writer) {
            try {
                log("Closing logger...");      
                writer.close();      
            } catch(IOException e) {
                e.printStackTrace();
            }    
        }
    }

    public void log(CharSequence text) {
        try{
            synchronized(writer) {
                writer.append(String.format("[%s][%s] %s%n", "INFO", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)), text));
                
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void logf(String format, Object... args) {
        log(String.format(format, args));
    }

    public void warn(CharSequence text) {
        try{
            synchronized(writer) {
                writer.append(String.format("[%s][%s] %s%n", "WARN", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)), text));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void warnf(String format, Object... args) {
        warn(String.format(format, args));
    }

    public void error(CharSequence text) {
        try{
            synchronized(writer) {
                writer.append(String.format("[%s][%s] %s%n", "ERROR", LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)), text));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void errorf(String format, Object... args) {
        error(String.format(format, args));
    }



    
}