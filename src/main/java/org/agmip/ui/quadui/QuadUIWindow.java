package org.agmip.ui.quadui;

import java.net.URL;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Scanner;


import org.apache.pivot.beans.Bindable;
import org.apache.pivot.collections.Map;
import org.apache.pivot.util.Resources;
import org.apache.pivot.util.Filter;
import org.apache.pivot.util.concurrent.Task;
import org.apache.pivot.util.concurrent.TaskListener;
import org.apache.pivot.wtk.Action;
import org.apache.pivot.wtk.ActivityIndicator;
import org.apache.pivot.wtk.Alert;
import org.apache.pivot.wtk.BoxPane;
import org.apache.pivot.wtk.Button;
import org.apache.pivot.wtk.ButtonPressListener;
import org.apache.pivot.wtk.Checkbox;
import org.apache.pivot.wtk.Component;
import org.apache.pivot.wtk.DesktopApplicationContext;
import org.apache.pivot.wtk.FileBrowserSheet;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.MessageType;
import org.apache.pivot.wtk.Orientation;
import org.apache.pivot.wtk.PushButton;
import org.apache.pivot.wtk.Sheet;
import org.apache.pivot.wtk.SheetCloseListener;
import org.apache.pivot.wtk.TaskAdapter;
import org.apache.pivot.wtk.TextInput;
import org.apache.pivot.wtk.Window;


import static org.agmip.util.JSONAdapter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuadUIWindow extends Window implements Bindable {
    private static Logger LOG = LoggerFactory.getLogger(QuadUIWindow.class);
    private ActivityIndicator convertIndicator = null;
    private PushButton convertButton = null;
    private PushButton browseToConvert = null;
    private PushButton browseOutputDir = null;
    private Checkbox modelApsim = null;
    private Checkbox modelDssat = null;
    private Checkbox modelJson = null;
    private TextInput outputText = null;
    private TextInput convertText = null;
    private ArrayList<Checkbox> checkboxGroup = new ArrayList<Checkbox>();
    private ArrayList<String> errors = new ArrayList<String>();

    public QuadUIWindow() {
        Action.getNamedActions().put("fileQuit", new Action() {
            @Override
            public void perform(Component src) {
                DesktopApplicationContext.exit();
            }
        });
    }

    private ArrayList<String> validateInputs() {
        ArrayList<String> errors = new ArrayList<String>();
        boolean anyModelChecked = false;
        for (Checkbox cbox : checkboxGroup) {
            if (cbox.isSelected()) {
                anyModelChecked = true;
            }
        }
        if (!anyModelChecked) {
            errors.add("You need to select an output format");
        }
        File convertFile = new File(convertText.getText());
        File outputDir = new File(outputText.getText());
        if (!convertFile.exists()) {
            errors.add("You need to select a file to convert");
        }
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            errors.add("You need to select an output directory");
        }
        return errors;
    }

    public void initialize(Map<String, Object> ns, URL location, Resources res) {
        convertIndicator = (ActivityIndicator) ns.get("convertIndicator");
        convertButton = (PushButton) ns.get("convertButton");
        browseToConvert = (PushButton) ns.get("browseConvertButton");
        browseOutputDir = (PushButton) ns.get("browseOutputButton");
        convertText = (TextInput) ns.get("convertText");
        outputText = (TextInput) ns.get("outputText");
        modelApsim = (Checkbox) ns.get("model-apsim");
        modelDssat = (Checkbox) ns.get("model-dssat");
        modelJson = (Checkbox) ns.get("model-json");
        checkboxGroup.add(modelApsim);
        checkboxGroup.add(modelDssat);
        checkboxGroup.add(modelJson);

        convertButton.getButtonPressListeners().add(new ButtonPressListener() {

            public void buttonPressed(Button button) {
                ArrayList<String> validationErrors = validateInputs();
                if (validationErrors.size() != 0) {
                    final BoxPane pane = new BoxPane(Orientation.VERTICAL);
                    for (String error : validationErrors) {
                        pane.add(new Label(error));
                    }
                    Alert.alert(MessageType.ERROR, "Cannot Convert", pane, QuadUIWindow.this);
                    return;
                }
                LOG.info("Starting translation job");
                try {
                    startTranslation();
                } catch(Exception ex) {
                    LOG.error(getStackTrace(ex));
                    
                }
            }
        });

        browseToConvert.getButtonPressListeners().add(new ButtonPressListener() {
            @Override
            public void buttonPressed(Button button) {
                final FileBrowserSheet browse = new FileBrowserSheet();
                browse.setMode(FileBrowserSheet.Mode.OPEN);
                browse.setDisabledFileFilter(new Filter<File>() {

                    @Override
                    public boolean include(File file) {
                        return (file.isFile()
                                && (!file.getName().toLowerCase().endsWith(".csv")
                                && (!file.getName().toLowerCase().endsWith(".zip")
                                && (!file.getName().toLowerCase().endsWith(".json")
                                && (!file.getName().toLowerCase().endsWith(".agmip"))))));
                    }
                });
                browse.open(QuadUIWindow.this, new SheetCloseListener() {
                    @Override
                    public void sheetClosed(Sheet sheet) {
                        if (sheet.getResult()) {
                            File convertFile = browse.getSelectedFile();
                            convertText.setText(convertFile.getPath());
                            if (outputText.getText().contains("")) {
                                try {
                                outputText.setText(convertFile.getCanonicalFile().getParent()); 
                                } catch (IOException ex) {}
                            }
                        }
                    }
                });
            }
        });

        browseOutputDir.getButtonPressListeners().add(new ButtonPressListener() {
            @Override
            public void buttonPressed(Button button) {
                final FileBrowserSheet browse = new FileBrowserSheet();
                browse.setMode(FileBrowserSheet.Mode.SAVE_TO);
                browse.open(QuadUIWindow.this, new SheetCloseListener() {
                    @Override
                    public void sheetClosed(Sheet sheet) {
                        if (sheet.getResult()) {
                            File outputDir = browse.getSelectedFile();
                            outputText.setText(outputDir.getPath());
                        }
                    }
                });
            }
        });
    }

    private void startTranslation() throws Exception {
        convertIndicator.setActive(true);
        if (convertText.getText().endsWith(".json")) {
            try {
                // Load the JSON representation into memory and send it down the line.
                String json = new Scanner(new File(convertText.getText()), "UTF-8").useDelimiter("\\A").next();
                LinkedHashMap data = fromJSON(json);
                toOutput(data);
            } catch (Exception ex) {
                LOG.error(getStackTrace(ex));
            }
        } else {
            TranslateFromTask task = new TranslateFromTask(convertText.getText());
            TaskListener<LinkedHashMap> listener = new TaskListener<LinkedHashMap>() {

                @Override
                public void taskExecuted(Task<LinkedHashMap> t) {
                    LinkedHashMap data = t.getResult();
                    if (!data.containsKey("errors")) {
                        toOutput(data);
                    } else {
                        Alert.alert(MessageType.ERROR, (String) data.get("errors"), QuadUIWindow.this);
                    }
                }

                @Override
                public void executeFailed(Task<LinkedHashMap> arg0) {
                    Alert.alert(MessageType.ERROR, arg0.getFault().getMessage(), QuadUIWindow.this);
                    LOG.error(getStackTrace(arg0.getFault()));
                    convertIndicator.setActive(false);
                    convertButton.setEnabled(true);
                }
            };
            task.execute(new TaskAdapter<LinkedHashMap>(listener));
        }
    }

    private void toOutput(LinkedHashMap map) {
        ArrayList<String> models = new ArrayList<String>();
        if (modelJson.isSelected()) {
            models.add("JSON");
        }
        if (modelApsim.isSelected()) {
            models.add("APSIM");
        }
        if (modelDssat.isSelected()) {
            models.add("DSSAT");
        }

        if (models.size() == 1 && models.get(0).equals("JSON")) {
            DumpToJson task = new DumpToJson(convertText.getText(), outputText.getText(), map);
            TaskListener<String> listener = new TaskListener<String>() {

                @Override
                public void taskExecuted(Task<String> t) {
                    Alert.alert(MessageType.INFO, "Translation completed", QuadUIWindow.this);
                    convertIndicator.setActive(false);
                    convertButton.setEnabled(true);
                }

                @Override
                public void executeFailed(Task<String> arg0) {
                    Alert.alert(MessageType.ERROR, arg0.getFault().getMessage(), QuadUIWindow.this);
                    LOG.error(getStackTrace(arg0.getFault()));
                    convertIndicator.setActive(false);
                    convertButton.setEnabled(true);
                }
            };
            task.execute(new TaskAdapter<String>(listener));
        } else {
            if (models.indexOf("JSON") != -1) {
                DumpToJson task = new DumpToJson(convertText.getText(), outputText.getText(), map);
                TaskListener<String> listener = new TaskListener<String>() {

                    @Override
                    public void taskExecuted(Task<String> t) {
                    }

                    @Override
                    public void executeFailed(Task<String> arg0) {
                        Alert.alert(MessageType.ERROR, arg0.getFault().getMessage(), QuadUIWindow.this);
                        LOG.error(getStackTrace(arg0.getFault()));
                        convertIndicator.setActive(false);
                        convertButton.setEnabled(true);
                    }
                };
                task.execute(new TaskAdapter<String>(listener));
            }
            TranslateToTask task = new TranslateToTask(models, map, outputText.getText());
            TaskListener<String> listener = new TaskListener<String>() {

                @Override
                public void executeFailed(Task<String> arg0) {
                    Alert.alert(MessageType.ERROR, arg0.getFault().getMessage(), QuadUIWindow.this);
                    LOG.error(getStackTrace(arg0.getFault()));
                    convertIndicator.setActive(false);
                    convertButton.setEnabled(true);
                }

                @Override
                public void taskExecuted(Task<String> arg0) {
                    Alert.alert(MessageType.INFO, "Translation completed", QuadUIWindow.this);
                    convertIndicator.setActive(false);
                    convertButton.setEnabled(true);
                    LOG.info("Completed translation job");
                }
            };
            task.execute(new TaskAdapter<String>(listener));
        }
    }

    private static String getStackTrace(Throwable aThrowable) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
    }
}
