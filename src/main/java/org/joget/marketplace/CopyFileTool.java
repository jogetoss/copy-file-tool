package org.joget.marketplace;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.commons.util.LogUtil;

public class CopyFileTool extends DefaultApplicationPlugin {

    private static final String OPT_PATH = "FILE_PATH";
    private static final String OPT_FROM_FIELD = "FORM_FIELD";
    private final static String MESSAGE_PATH = "messages/CopyFileTool";

    @Override
    public Object execute(Map properties) {

        String filePath = "";
        String formDefId = getPropertyString("formDefId");
        String fileFieldId = getPropertyString("fileFieldId");
        String pathOptions = getPropertyString("pathOptions");
        String sourceFileRecordId = getPropertyString("sourceFileRecordId");
        String outputFileRecordId = getPropertyString("outputFileRecordId");

        AppService appService = (AppService) FormUtil.getApplicationContext().getBean("appService");
        String recordId;
        AppDefinition appDef = (AppDefinition) properties.get("appDef");
        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        if (wfAssignment != null) {
            if (sourceFileRecordId != null && sourceFileRecordId.equals("")) {
                sourceFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
            if (outputFileRecordId != null && outputFileRecordId.equals("")) {
                outputFileRecordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            }
        }


        if (OPT_PATH.equalsIgnoreCase(pathOptions)) {
            filePath = getPropertyString("filePath");
        } else if (OPT_FROM_FIELD.equalsIgnoreCase(pathOptions)) {
            String pathFormDefId = getPropertyString("pathFormDefId");
            String pathFileFieldId = getPropertyString("pathFileFieldId");

            FormRowSet rows = appService.loadFormData(appDef.getAppId(), String.valueOf(appDef.getVersion()), pathFormDefId, sourceFileRecordId);
            if (rows != null && !rows.isEmpty()) {
                FormRow formRow = rows.get(0);
                String uploadedFilename = formRow.getProperty(pathFileFieldId);

                if (uploadedFilename != null && !uploadedFilename.isEmpty()) {
                    if (!uploadedFilename.contains("/") && !uploadedFilename.contains("\\")) {
                        String sourceTableName = appService.getFormTableName(appDef, pathFormDefId);

                        if (uploadedFilename.contains(";")) {
                            copyMultipleFiles(uploadedFilename, sourceTableName, sourceFileRecordId, formDefId, fileFieldId, appService, appDef, outputFileRecordId);
                        } else {
                            copySingleFile(uploadedFilename, sourceTableName, sourceFileRecordId, formDefId, fileFieldId, appService, appDef, outputFileRecordId);
                        }

                    } else {
                        filePath = uploadedFilename;
                    }
                }
            }

        }
        FormRowSet frs = appService.loadFormData(appDef.getAppId(), String.valueOf(appDef.getVersion()), formDefId, outputFileRecordId);
        if (frs == null || frs.isEmpty()) {
            outputFileRecordId = UuidGenerator.getInstance().getUuid();
        }

        // read the file
        File sourceFile = new File(filePath);
        if (sourceFile.exists()) {
            String fileName = sourceFile.getName();
            String tableName = appService.getFormTableName(appDef, formDefId);
            //String id = UuidGenerator.getInstance().getUuid();
            FileUtil.storeFile(sourceFile, tableName, outputFileRecordId);
            FormRowSet rows = new FormRowSet();
            FormRow row = new FormRow();
            row.setId(outputFileRecordId);
            row.put(fileFieldId, fileName);
            row.put("id", outputFileRecordId);
            rows.add(row);

            appService.storeFormData(formDefId, tableName, rows, outputFileRecordId);

        }

        return null;
    }

    private void copyMultipleFiles(String filenames, String sourceTableName, String sourceFileRecordId, String formDefId, String fileFieldId, AppService appService, AppDefinition appDef, String outputFileRecordId) {
        String[] files = filenames.split(";");
        StringBuilder savedFilenames = new StringBuilder();

        for (String filename : files) {
            filename = filename.trim();
            if (!filename.isEmpty()) {
                try {
                    File uploadedFile = FileUtil.getFile(filename, sourceTableName, sourceFileRecordId);
                    if (uploadedFile != null && uploadedFile.exists()) {
                        String tableName = appService.getFormTableName(appDef, formDefId);
                        FileUtil.storeFile(uploadedFile, tableName, outputFileRecordId);

                        if (savedFilenames.length() > 0) {
                            savedFilenames.append(";");
                        }
                        savedFilenames.append(filename);

                    } 
                } catch (IOException e) {
                    LogUtil.error("CopyFileTool", e, "Failed to copy uploaded file: " + filename);
                }
            }
        }

        if (savedFilenames.length() > 0) {
            try {
                String tableName = appService.getFormTableName(appDef, formDefId);
                FormRowSet rowSet = new FormRowSet();
                FormRow newRow = new FormRow();
                newRow.setId(outputFileRecordId);
                newRow.put(fileFieldId, savedFilenames.toString()); 
                newRow.put("id", outputFileRecordId);
                rowSet.add(newRow);

                appService.storeFormData(formDefId, tableName, rowSet, outputFileRecordId);

            } catch (Exception e) {
                LogUtil.error("CopyFileTool", e, "Failed to save multiple filenames.");
            }
        }
    }

    private void copySingleFile(String filename, String sourceTableName, String sourceFileRecordId, String formDefId, String fileFieldId, AppService appService, AppDefinition appDef, String outputFileRecordId) {
        try {
            File uploadedFile = FileUtil.getFile(filename.trim(), sourceTableName, sourceFileRecordId);

            if (uploadedFile != null && uploadedFile.exists()) {
                String tableName = appService.getFormTableName(appDef, formDefId);

                FileUtil.storeFile(uploadedFile, tableName, outputFileRecordId);

                FormRowSet rowSet = new FormRowSet();
                FormRow newRow = new FormRow();
                newRow.setId(outputFileRecordId);
                newRow.put(fileFieldId, filename.trim());
                newRow.put("id", outputFileRecordId);
                rowSet.add(newRow);

                appService.storeFormData(formDefId, tableName, rowSet, outputFileRecordId);

            } 
        } catch (IOException e) {
            LogUtil.error("CopyFileTool", e, "Failed to copy single file: " + filename);
        }
    }

    @Override
    public String getName() {
        return "Copy File Tool";
    }

    @Override
    public String getVersion() {
        return Activator.VERSION;
    }

    @Override
    public String getDescription() {
        return "Copy File Tool";
    }

    @Override
    public String getLabel() {
        return "Copy File Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/copyFileTool.json", null, true, MESSAGE_PATH);
    }

}
