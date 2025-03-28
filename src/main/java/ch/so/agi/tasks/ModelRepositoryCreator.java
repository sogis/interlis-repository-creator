package ch.so.agi.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.ili2c.metamodel.Model;
import ch.ehi.basics.settings.Settings;
import org.interlis2.validator.Validator;

public class ModelRepositoryCreator extends DefaultTask {
    private Logger log = Logging.getLogger(this.getClass());
    
    private TransferDescription tdRepository = null;
    private IoxWriter ioxWriter = null;
    private final static String BID="b1";

    private final List<String> repoModelNames = List.of("IliRepository09", "IliRepository20");
    
    private Object modelsDir = null;

    @Optional
    private Object dataFile = "ilimodels.xml";
   
    @Optional
    private String technicalContact = "mailto:agi@bd.so.ch";

    @Optional
    private String modelRepos = "https://models.interlis.ch/;https://models.kgk-cgc.ch/;https://models.geo.admin.ch/";
    
    @Optional 
    private Boolean ilismeta = false;
    
    @Optional 
    private String repoModelName = "IliRepository20";
    
    @TaskAction
    public void writeIliModelsFile() {        
        if (modelsDir == null) {
            throw new IllegalArgumentException("modelsDir must not be null");
        }
        if (dataFile == null) {
            throw new IllegalArgumentException("dataFile must not be null");
        }
        if (!repoModelNames.contains(repoModelName)) {
            throw new IllegalArgumentException("modelRepoName is not a valid model name");
        }

        try {
            boolean valid = createXmlFile(this.getProject().file(dataFile).getAbsolutePath(), this.getProject().file(modelsDir));
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

    @Input
    public Object getModelsDir() {
        return modelsDir;
    } 
    
    public void setModelsDir(Object modelsDir) {
        this.modelsDir = modelsDir;
    }

    @OutputFile
    public Object getDataFile() {
        return dataFile;
    } 
    
    public void setDataFile(Object dataFile) {
        this.dataFile = dataFile;
    }
    
    @Input
    public Object getTechnicalContact() {
        return technicalContact;
    } 
    
    public void setTechnicalContact(String technicalContact) {
        this.technicalContact = technicalContact;
    }
    
    @Input
    public String getModelRepos() {
        return modelRepos;
    }
    
    public void setModelRepos(String modelRepos) {
        this.modelRepos = modelRepos;
    }
    
    @Input
    public Boolean getIlismeta() {
    	return ilismeta;
    }
    
    public void setIlismeta(Boolean ilismeta) {
    	this.ilismeta = ilismeta;
    }
    
    @Input
    public String getRepoModelName() {
        return repoModelName;
    }

    public void setRepoModelName(String repoModelName) {
        this.repoModelName = repoModelName;
    }

    private boolean createXmlFile(String outputFileName, File modelsDir) throws Ili2cException, IoxException, IOException {
        String ILI_TOPIC=getRepoModelName()+".RepositoryIndex";
        String ILI_CLASS=ILI_TOPIC+".ModelMetadata";
        String ILI_STRUCT_MODELNAME=getRepoModelName()+".ModelName_";
        
        tdRepository = getTransferDescriptionFromModelName(getRepoModelName());

        File outputFile = new File(outputFileName);
        ioxWriter = new XtfWriter(outputFile, tdRepository);
        ioxWriter.write(new ch.interlis.iox_j.StartTransferEvent("SOGIS-INTERLIS-REPOSITORY-CREATOR", "", null));
        ioxWriter.write(new ch.interlis.iox_j.StartBasketEvent(ILI_TOPIC,BID));

        List<Path> models = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(modelsDir.toPath())) {
            models = walk
                    .filter(p -> !Files.isDirectory(p))   
                    .filter(f -> isEndWith(f.toString()))
                    .collect(Collectors.toList());        
        }

        // In einem ersten Durchlauf werden die Verzeichnisse eruiert, damit diese
        // als (lokales) Repository beim Kompilieren verwendet werden können.
        // Welches Problem löst das? Importieren lokale Modelle wiederum lokale Modelle,
        // werden diese entweder nicht gefunden (falls sie in keinem Online-Repo sind) oder
        // sie werden aus einem bestehenden/vorhanden Repository verwendet. Dies sollte 
        // m.E. nicht die Regel sein.
        Set<String> parentModelDirSet = new TreeSet<>();
        for (Path model : models) {
            if (model.toAbsolutePath().toString().contains("replaced")) {
                continue;
            } 
            parentModelDirSet.add(model.toFile().getAbsoluteFile().getParent());
        }
        List<String> repositories = new ArrayList<>();
        repositories.add("models-ext");
        repositories.addAll(parentModelDirSet);
        repositories.addAll(Arrays.asList(modelRepos.split(";")));

        int i = 1;
        for (Path modelPath : models) {
            File file = modelPath.toFile();
            
            // Abgelöste Modelle werden nicht im Fileindex (ilimodels.xml) 
            // aufgelistet. Auch weil wir immer neue Modellnamen machen und
            // keine precursorVersion-Semantik haben.
            // Andere Variante wäre isBrowseOnly=true.
            if(file.getAbsolutePath().toLowerCase().contains("replaced")) {
                continue;
            }
            
            TransferDescription td = getTransferDescriptionFromFileName(file.getAbsolutePath(), repositories.toArray(new String[0]));            

            // IMD output
            if (ilismeta) {
            	File ilismetaDir = Paths.get(modelsDir.getParent(), "ilismeta").toFile();            
            	File ilismetaFile = Paths.get(ilismetaDir.getAbsolutePath(), FilenameUtils.removeExtension(file.getName()) + ".xml").toFile();
    			ch.interlis.ili2c.generator.Imd16Generator.generate(ilismetaFile, td, TransferDescription.getVersion());
		    }
		                
            // Mehrere Modelle in einer ili-Datei.
            for (Model lastModel : td.getModelsFromLastFile()) {
                Iom_jObject iomObj = new Iom_jObject(ILI_CLASS, String.valueOf(i));
                iomObj.setattrvalue("Name", lastModel.getName());

                if (lastModel.getIliVersion().equalsIgnoreCase("1")) {
                    iomObj.setattrvalue("SchemaLanguage", "ili1");
                } else if (lastModel.getIliVersion().equalsIgnoreCase("2.2")) {
                    iomObj.setattrvalue("SchemaLanguage", "ili2_2");
                } else if (lastModel.getIliVersion().equalsIgnoreCase("2.3")) {
                    iomObj.setattrvalue("SchemaLanguage", "ili2_3");
                } else if (lastModel.getIliVersion().equalsIgnoreCase("2.4")) {
                    iomObj.setattrvalue("SchemaLanguage", "ili2_4");
                }
                
                String filePath = file.getAbsoluteFile().getParent().replace(modelsDir.getAbsolutePath()+FileSystems.getDefault().getSeparator(), "");
                iomObj.setattrvalue("File", filePath + "/" + file.getName());

                if (lastModel.getModelVersion() == null) {
                    iomObj.setattrvalue("Version", "2000-01-01");
                } else {
                    iomObj.setattrvalue("Version", lastModel.getModelVersion());
                }
                
                try {
                    iomObj.setattrvalue("Issuer", lastModel.getIssuer());
                } catch (IllegalArgumentException e) {
                    // do nothing
                }
                
                String modelTechnicalContact = lastModel.getMetaValue("technicalContact");
                if (modelTechnicalContact != null) {
                    if (isValidEmail(modelTechnicalContact) && !modelTechnicalContact.startsWith("mailto")) {
                        iomObj.setattrvalue("technicalContact", "mailto:"+modelTechnicalContact);     
                    } else {
                        iomObj.setattrvalue("technicalContact", lastModel.getMetaValue("technicalContact"));     
                    }
                } else {
                    iomObj.setattrvalue("technicalContact", technicalContact);
                }
                
                String furtherInformation = lastModel.getMetaValue("furtherInformation"); 
                if (furtherInformation != null && furtherInformation.startsWith("http")) {
                    iomObj.setattrvalue("furtherInformation", furtherInformation);
                }
                
                String title = lastModel.getMetaValue("Title");                
                if (title != null) {
                    iomObj.setattrvalue("Title", title);
                }
                
                String shortDescription = lastModel.getMetaValue("shortDescription");
                if (shortDescription != null) {
                    iomObj.setattrvalue("shortDescription", shortDescription);
                }
                
                try (InputStream is = Files.newInputStream(Paths.get(file.getAbsolutePath()))) {
                    String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
                    iomObj.setattrvalue("md5", md5);
                }

                // imports                
                for (Model model : lastModel.getImporting()) {
                    Iom_jObject iomObjDependsOnModel = new Iom_jObject(ILI_STRUCT_MODELNAME, null);
                    
                    // Das Modell INTERLIS wird nicht gefunden beim --check-repo-ilis Test.
                    if (!model.getName().equalsIgnoreCase("INTERLIS")) {
                        iomObjDependsOnModel.setattrvalue("value",  model.getName());
                        iomObj.addattrobj("dependsOnModel", iomObjDependsOnModel);
                    }
                }
                
                // translationOf
                if (lastModel.getTranslationOf() != null) {
                    Iom_jObject iomObjDependsOnModel = new Iom_jObject(ILI_STRUCT_MODELNAME, null);
                    iomObjDependsOnModel.setattrvalue("value", lastModel.getTranslationOf().getName());
                    iomObj.addattrobj("dependsOnModel", iomObjDependsOnModel);
                }
                
                ioxWriter.write(new ch.interlis.iox_j.ObjectEvent(iomObj));   
                i++;  
            }
        }
        
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
    
    // Methode wird nur zum Kompilieren von IliRepositoryXX benötigt.
    // TODO: Abgrenzung / Synergien mit getTransferDescriptionFromFileName?
    private TransferDescription getTransferDescriptionFromModelName(String iliModelName) throws Ili2cException {
        Path targetPath = null;
        try (InputStream inputStream = ModelRepositoryCreator.class.getClassLoader().getResourceAsStream("IliRepository20.ili")) {
            if (inputStream == null) {
                throw new IOException("Resource not found");
            }

            Path tempDirectory = Files.createTempDirectory("ili_");
            targetPath = tempDirectory.resolve("IliRepository20.ili");
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("File copied to: " + targetPath);
        } catch (IOException e) {
            e.printStackTrace();
            throw new Ili2cException(e);
        }

        IliManager manager = new IliManager();
        String repositories[] = new String[] { targetPath.getParent().toString() };
        manager.setRepositories(repositories);
        ArrayList<String> modelNames = new ArrayList<String>();
        modelNames.add(iliModelName);
        Configuration config = manager.getConfig(modelNames, 2.3);
        ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);

        if (iliTd == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed"); // TODO: can this be tested?
        }
        
        return iliTd;
    }
    
    private TransferDescription getTransferDescriptionFromFileName(String fileName, String[] repositories) throws Ili2cException {
        IliManager manager = new IliManager();        
        manager.setRepositories(repositories);
        
        ArrayList<String> ilifiles = new ArrayList<String>();
        ilifiles.add(fileName);
        Configuration config = manager.getConfigWithFiles(ilifiles);
        ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);
                
        if (iliTd == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed");
        }
        
        return iliTd;
    }
    
    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\."+
                "[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
                "A-Z]{2,7}$";
        Pattern pat = Pattern.compile(emailRegex);
        if (email == null) {
            return false;
        } 
        return pat.matcher(email).matches();
    }
    
    private static boolean isEndWith(String file) {
        if (file.toLowerCase().endsWith("ili")) {
            return true;
        }
        return false;
    }
}
