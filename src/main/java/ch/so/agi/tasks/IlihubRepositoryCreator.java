package ch.so.agi.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
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
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.ObjectEvent;

public class IlihubRepositoryCreator extends DefaultTask {
    private Logger log = Logging.getLogger(this.getClass());
    
    private TransferDescription tdRepository = null;
    private IoxWriter ioxWriter = null;
    private XtfReader xtfReader = null;
    
    private static final String BID="b1";
    private static final String REPO_MODEL_NAME = "DatasetIdx16";

    private File reposDir = null;
    
    @Optional
    private Object dataFile = "ilidata.xml";

    @TaskAction
    public void writeIliDataFile() {        
        if (reposDir == null) {
            throw new IllegalArgumentException("reposDir must not be null");
        }

        if (dataFile == null) {
            throw new IllegalArgumentException("dataFile must not be null");
        }

        try {
            boolean valid = createXmlFile(this.getProject().file(dataFile).getAbsolutePath(), this.getProject().file(reposDir));
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
    
    @InputDirectory
    public Object getReposDir() {
        return reposDir;
    } 
    
    public void setReposDir(File reposDir) {
        this.reposDir = reposDir;
    }

    @OutputFile
    public Object getDataFile() {
        return dataFile;
    } 

    public void setDataFile(Object dataFile) {
        this.dataFile = dataFile;
    }

    private boolean createXmlFile(String outputFileName, File reposDir) throws Ili2cException, IoxException, IOException {
        String ILI_TOPIC=REPO_MODEL_NAME+".DataIndex";
        String ILI_CLASS=ILI_TOPIC+".DatasetMetadata";
        //String ILI_STRUCT_MODELNAME=getRepoModelName()+".ModelName_";

        tdRepository = getTransferDescriptionFromModelName(REPO_MODEL_NAME);

        File outputFile = new File(outputFileName);
        ioxWriter = new XtfWriter(outputFile, tdRepository);
        ioxWriter.write(new ch.interlis.iox_j.StartTransferEvent("SOGIS-INTERLIS-REPOSITORY-CREATOR", "", null));
        ioxWriter.write(new ch.interlis.iox_j.StartBasketEvent(ILI_TOPIC,BID));

        List<Path> repos = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(reposDir.toPath(), 1)) {
            repos = walk
                    .filter(p -> Files.isDirectory(p))
                    .filter(d -> {
                        if (!d.toFile().getAbsolutePath().equalsIgnoreCase(reposDir.getAbsolutePath())) {
                            return true;
                        } else {
                            return false;
                        }
                    })
//                    .map(Path::toString)

//                    .filter(f -> isEndWith(f.toString()))
                    .collect(Collectors.toList());        
        }
        //repos.forEach(System.out::println);

        for (Path repo : repos) {
            System.out.println("*************** neues repo ******************");
            
            File iliDataSrcFile = Paths.get(repo.toFile().getAbsolutePath(), "ilidata.xml").toFile();
            
            xtfReader = new XtfReader(iliDataSrcFile);
            IoxEvent event = xtfReader.read();
            while (event instanceof IoxEvent) {
                if (event instanceof ObjectEvent) {
                    ObjectEvent objectEvent = (ObjectEvent) event;
                    IomObject iomObj = objectEvent.getIomObject();
//                    System.out.println("iomObj: " + iomObj);
                    
                    for (int i=0;i<iomObj.getattrvaluecount("files");i++) {
                        IomObject dataFileObj = iomObj.getattrobj("files", i);
                        System.out.println("dataFileObj: " + dataFileObj);
                        
                        for (int j=0;j<dataFileObj.getattrvaluecount("file");j++) {
                            IomObject fileObj = dataFileObj.getattrobj("file", j);
                            System.out.println("fileObj: " + fileObj.getattrvalue("path"));
                            
                            String origPath = FilenameUtils.separatorsToSystem(fileObj.getattrvalue("path"));
                            //System.out.println(repo.getFileName().toString());
                            String newPath = Paths.get(repo.getFileName().toString(), origPath).toString();
                            System.out.println(newPath);

                        }
                        
                    }
                    
                    
//                    if (iomObj.getobjectoid().equalsIgnoreCase(id)) {
//                        return iomObj;
//                    }
                }
                event = xtfReader.read();
            }

        }
        
        
        
        ioxWriter.write(new ch.interlis.iox_j.EndBasketEvent());
        ioxWriter.write(new ch.interlis.iox_j.EndTransferEvent());
        ioxWriter.flush();
        ioxWriter.close();
        
        System.out.println(outputFileName);
        
        // TODO
        // Kann man mit einem Constraint testen, ob der Ordnernamen mit einem Amtskürzel beginnt?
        //https://github.com/claeis/iox-ili/blob/master/src/main/java/ch/interlis/iox_j/validator/functions/Text.java#L231
        
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
