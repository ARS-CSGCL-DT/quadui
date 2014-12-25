package org.agmip.ui.quadui;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.agmip.core.types.TranslatorOutput;

import com.google.common.io.Files;
import java.util.Date;
import org.agmip.common.Functions;


public class TranslateRunner implements Runnable {
    private TranslatorOutput translator;
    private HashMap data;
    private String outputDirectory;
    private String model;
    private boolean compress;
    private static Logger LOG = LoggerFactory.getLogger(TranslateRunner.class);

    public TranslateRunner(TranslatorOutput translator, HashMap data, String outputDirectory, String model, boolean compress) {
        this.translator = translator;
        this.data = data;
        this.outputDirectory = outputDirectory;
        this.model = model;
        this.compress = compress;
    }


    @Override
    public void run() {
        LOG.debug("Starting new thread!");
        long timer = System.currentTimeMillis();
        try {
            translator.writeFile(outputDirectory, data);
            if (compress) {
                compressOutput(outputDirectory, model);
            }
            timer = System.currentTimeMillis() - timer;
            LOG.info("{} Translator Finished in {}s.", model, timer/1000.0);
        } catch (IOException e) {
            LOG.error("{} translator got an error.", model);
            LOG.error(Functions.getStackTrace(e));
        }
    }

    private static void compressOutput(String outputDirectory, String model) throws IOException {
        File directory = new File(outputDirectory);
        File zipFile   = new File(directory, model+"_Input.zip");
        List<File> files   = Arrays.asList(directory.listFiles());
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream  zos = new ZipOutputStream(fos);
        for(File f : files) {
            ZipEntry ze = new ZipEntry(f.getName());
            zos.putNextEntry(ze);
            zos.write(Files.toByteArray(f));
            zos.closeEntry();
            f.delete();
        }
        zos.close();
        fos.close();
    }
}
