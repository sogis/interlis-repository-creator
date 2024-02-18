package ch.so.agi.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.interlis2.validator.Validator;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;

public class ConfigDataRepositoryCreator extends DefaultTask {
    private Logger log = Logging.getLogger(this.getClass());

    private TransferDescription tdRepository = null;
    private IoxWriter ioxWriter = null;
    private static final String BID="b1";
    private static final String REPO_MODEL_NAME = "DatasetIdx16";
    private static final String CONFIG_CODE_ILIVALIDATORCONFIG = "http://codes.interlis.ch/type/ilivalidatorconfig";
    private static final String CONFIG_CODE_METACONFIG = "http://codes.interlis.ch/type/metaconfig";
    
    private Object configDir = null;

    @Optional
    private Object dataFile = "ilidata.xml";
   
    private String owner = "mailto:agi@bd.so.ch";
    
    @Input
    public Object getConfigDir() {
        return configDir;
    }

    public void setConfigDir(Object configDir) {
        this.configDir = configDir;
    }

    @OutputFile
    public Object getDataFile() {
        return dataFile;
    }

    public void setDataFile(Object dataFile) {
        this.dataFile = dataFile;
    }

    @Input
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @TaskAction
    public void writeIliDataFile() {        
        if (configDir == null) {
            throw new IllegalArgumentException("configDir must not be null");
        }
        if (dataFile == null) {
            throw new IllegalArgumentException("dataFile must not be null");
        }

        try {
            boolean valid = createXmlFile(this.getProject().file(dataFile).getAbsolutePath(), this.getProject().file(configDir));
            if (!valid) {
                throw new GradleException("generated " + dataFile.toString() + " is not valid");
            }
        } catch (Ili2cException | IoxException | IOException e) {
            e.printStackTrace();
            log.error("failed to create " + dataFile.toString());
            log.error(e.getMessage());
            GradleException ge = new GradleException();
            throw ge;
        } 
    }
    
    private boolean createXmlFile(String outputFileName, File configDir) throws Ili2cException, IoxException, IOException {
        String ILI_TOPIC=REPO_MODEL_NAME+".DataIndex";
        String ILI_CLASS=ILI_TOPIC+".DatasetMetadata";
        String ILI_STRUCT_CODE=REPO_MODEL_NAME+".Code_";
        String ILI_STRUCT_FILE=REPO_MODEL_NAME+".File";
        String ILI_STRUCT_DATA_FILE=REPO_MODEL_NAME+".DataFile";

        tdRepository = getTransferDescriptionFromModelName(REPO_MODEL_NAME);

        File outputFile = new File(outputFileName);
        ioxWriter = new XtfWriter(outputFile, tdRepository);
        ioxWriter.write(new ch.interlis.iox_j.StartTransferEvent("SOGIS-INTERLIS-REPOSITORY-CREATOR", "", null));
        ioxWriter.write(new ch.interlis.iox_j.StartBasketEvent(ILI_TOPIC,BID));

        List<Path> configFiles = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(configDir.toPath(), 2)) {
            configFiles = walk
                    .filter(p -> !Files.isDirectory(p))
                    .filter(f -> {
                        if (f.toFile().getAbsolutePath().toLowerCase().endsWith("ini")) {
                            return true;
                        } else {
                            return false;
                        }
                    })
                    .filter(f -> {
                        if (f.toFile().getAbsolutePath().toLowerCase().contains("replaced")) {
                            return false;
                        } else {
                            return true;
                        }
                    }) 
                    .collect(Collectors.toList());        
        }

        for (int i=1; i<=configFiles.size(); i++) {
            Path configFile = configFiles.get(i-1);

            Iom_jObject iomObj = new Iom_jObject(ILI_CLASS, String.valueOf(i));

            iomObj.setattrvalue("id", configFile.getFileName().toString().substring(0, configFile.getFileName().toString().length()-4));
            iomObj.setattrvalue("version", "current");
            iomObj.setattrvalue("owner", owner);

            Iom_jObject iomObjCategory = new Iom_jObject(ILI_STRUCT_CODE, null);
            if (configFile.toString().toLowerCase().endsWith("meta.ini")) {
                iomObjCategory.setattrvalue("value", CONFIG_CODE_METACONFIG);
            } else {
                iomObjCategory.setattrvalue("value", CONFIG_CODE_ILIVALIDATORCONFIG);                
            }
            iomObj.addattrobj("categories", iomObjCategory);
            

            Iom_jObject iomObjDataFile = new Iom_jObject(ILI_STRUCT_DATA_FILE, null);
            iomObjDataFile.setattrvalue("fileFormat", "text/plain");

            Iom_jObject iomObjFile = new Iom_jObject(ILI_STRUCT_FILE, null);
            String filePath = configFile.toFile().getAbsoluteFile().getParent().replace(configDir.getAbsolutePath()+FileSystems.getDefault().getSeparator(), "");
            iomObjFile.setattrvalue("path", filePath + "/" + configFile.toFile().getName());
            
            try (InputStream is = Files.newInputStream(configFile)) {
                String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
                iomObjFile.setattrvalue("md5", md5);
            }
            
            iomObjDataFile.addattrobj("file", iomObjFile);
            iomObj.addattrobj("files", iomObjDataFile);
            
            ioxWriter.write(new ch.interlis.iox_j.ObjectEvent(iomObj));   
        }
        
        //System.out.println(configFiles);
                
        ioxWriter.write(new ch.interlis.iox_j.EndBasketEvent());
        ioxWriter.write(new ch.interlis.iox_j.EndTransferEvent());
        ioxWriter.flush();
        ioxWriter.close();

        Settings settings = new Settings();
        settings.setValue(Validator.SETTING_ILIDIRS, Validator.SETTING_DEFAULT_ILIDIRS);
        settings.setValue(Validator.SETTING_ALL_OBJECTS_ACCESSIBLE, Validator.TRUE);
        boolean valid = Validator.runValidation(outputFile.getAbsolutePath(), settings);

        return valid;
    }
    
    private TransferDescription getTransferDescriptionFromModelName(String iliModelName) throws Ili2cException {
        IliManager manager = new IliManager();
        String repositories[] = new String[] { "http://models.interlis.ch/" };
        manager.setRepositories(repositories);
        ArrayList<String> modelNames = new ArrayList<String>();
        modelNames.add(iliModelName);
        Configuration config = manager.getConfig(modelNames, 2.3);
        ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);

        if (iliTd == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed"); 
        }
        
        return iliTd;
    }

}
