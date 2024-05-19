package org.joget.marketplace;

import java.io.File;
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

        AppService appService = (AppService) FormUtil.getApplicationContext().getBean("appService");
        String recordId;
        AppDefinition appDef = (AppDefinition) properties.get("appDef");
        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        if (wfAssignment != null) {
            recordId = appService.getOriginProcessId(wfAssignment.getProcessId());
        } else {
            recordId = (String) properties.get("recordId");
        }
        
        if (OPT_PATH.equalsIgnoreCase(pathOptions)) {
            filePath = getPropertyString("filePath");
        } else if (OPT_FROM_FIELD.equalsIgnoreCase(pathOptions)) {
            String pathFormDefId = getPropertyString("pathFormDefId");
            String pathFileFieldId = getPropertyString("pathFileFieldId");
            // get the file path
            FormRowSet rows = appService.loadFormData(appDef.getAppId(), String.valueOf(appDef.getVersion()), pathFormDefId, recordId);
            if (rows != null && !rows.isEmpty()) {
                FormRow formRow = rows.get(0);
                filePath = formRow.getProperty(pathFileFieldId);
            }
        }

        // first check if it is the same form to store the file
        // if its same form  the us recordId for file upload and if not the same form then generate new uuid
        FormRowSet frs = appService.loadFormData(appDef.getAppId(), String.valueOf(appDef.getVersion()), formDefId, recordId);
        if (frs == null || frs.isEmpty()) {
            recordId = UuidGenerator.getInstance().getUuid();
        }

        // read the file
        File sourceFile = new File(filePath);
        if (sourceFile.exists()) {
            String fileName = sourceFile.getName();
            String tableName = appService.getFormTableName(appDef, formDefId);
            //String id = UuidGenerator.getInstance().getUuid();
            FileUtil.storeFile(sourceFile, tableName, recordId);
            FormRowSet rows = new FormRowSet();
            FormRow row = new FormRow();
            row.setId(recordId);
            row.put(fileFieldId, fileName);
            row.put("id", recordId);
            rows.add(row);
            appService.storeFormData(formDefId, tableName, rows, recordId);
        }
        return null;
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
