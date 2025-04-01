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
import org.gradle.api.tasks.InputDirectory;
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
import ch.interlis.ili2c.generator.Imd16Generator;
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
        
    private File modelsDirectory = null;
    private File dataFile = new File("ilimodels.xml");
    private String technicalContact = "mailto:agi@bd.so.ch";
    private String modelRepos = "";
    private Boolean ilismeta = false;
    private String ignoredDirectories = "";
    
    @InputDirectory
    public File getModelsDirectory() {
        return modelsDirectory;
    } 
    
    @OutputFile
    @Optional
    public File getDataFile() {
        return dataFile;
    } 
    
    @Input
    @Optional
    public Object getTechnicalContact() {
        return technicalContact;
    } 
    
    @Input
    @Optional    
    public String getModelRepos() {
        return modelRepos;
    }
    
    @Input
    @Optional 
    public Boolean getIlismeta() {
        return ilismeta;
    }

    @Input
    @Optional
    public String getIgnoredDirectories() {
        return ignoredDirectories;
    }
    
    public void setModelsDirectory(File modelsDirectory) {
        this.modelsDirectory = modelsDirectory;
    }

    public void setDataFile(File dataFile) {
        this.dataFile = dataFile;
    }

    public void setTechnicalContact(String technicalContact) {
        this.technicalContact = technicalContact;
    }

    public void setModelRepos(String modelRepos) {
        this.modelRepos = modelRepos;
    }

    public void setIlismeta(Boolean ilismeta) {
        this.ilismeta = ilismeta;
    }

    public void setIgnoredDirectories(String ignoredDirectories) {
        this.ignoredDirectories = ignoredDirectories;
    }

    @TaskAction
    public void writeIliModelsFile() {        
        if (modelsDirectory == null) {
            throw new IllegalArgumentException("modelsDirectory must not be null");
        }
        if (dataFile == null) {
            throw new IllegalArgumentException("dataFile must not be null");
        }
        try {
            boolean valid = createXmlFile(this.getProject().file(dataFile).getAbsolutePath(), this.getProject().file(modelsDirectory), ignoredDirectories);
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

    private boolean createXmlFile(String outputFileName, File modelsDir, String ignoredDirectories) throws Ili2cException, IoxException, IOException {
        String ILI_TOPIC="IliRepository20.RepositoryIndex";
        String ILI_CLASS=ILI_TOPIC+".ModelMetadata";
        String ILI_STRUCT_MODELNAME="IliRepository20.ModelName_";
        
        tdRepository = getTransferDescription("IliRepository20");
        
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
        // m.E. nicht die Regel sein, weil sie ja geändert haben können.
        String[] items = ignoredDirectories.split(";");
        Set<String> parentModelDirSet = new TreeSet<>();
        for (Path model : models) {
            if (model.toAbsolutePath().toString().contains("replaced")) {
                continue;
            } 
            
            // Aus den zu ignorierenden Directories dürfen die Modelle auch nicht
            // zum Kompilieren anderer Modell verwendet werden. Dazu ist models-ext
            // da.
            if (Arrays.asList(items).contains(model.getParent().getFileName().toString())) {
                continue;
            }
            parentModelDirSet.add(model.toFile().getAbsoluteFile().getParent());
        }
        
        List<String> repositories = new ArrayList<>();
        repositories.add(Paths.get(modelsDir.getParent(), "models-ext").toString());
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
            
            // Aus den zu ignorierenden Directories dürfen die Modelle nicht berücksichtigt werden. 
            if (Arrays.asList(items).contains(modelPath.getParent().getFileName().toString())) {
                continue;
            }

            TransferDescription td = getTransferDescription(modelPath, repositories.toArray(new String[0]));            

            // IMD output
            if (ilismeta) {
            	File ilismetaDir = Paths.get(modelsDir.getParent(), "ilismeta").toFile();            
            	File ilismetaFile = Paths.get(ilismetaDir.getAbsolutePath(), FilenameUtils.removeExtension(file.getName()) + ".xml").toFile();
    			Imd16Generator.generate(ilismetaFile, td, TransferDescription.getVersion());
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

                // Importierte Modelle                
                for (Model model : lastModel.getImporting()) {
                    Iom_jObject iomObjDependsOnModel = new Iom_jObject(ILI_STRUCT_MODELNAME, null);
                    
                    // Das Modell INTERLIS wird nicht gefunden beim --check-repo-ilis Test.
                    if (!model.getName().equalsIgnoreCase("INTERLIS")) {
                        iomObjDependsOnModel.setattrvalue("value",  model.getName());
                        iomObj.addattrobj("dependsOnModel", iomObjDependsOnModel);
                    }
                }
                
                // translationOf Modelle
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
        settings.setValue(Validator.SETTING_ILIDIRS, String.join(";", repositories));
        settings.setValue(Validator.SETTING_ALL_OBJECTS_ACCESSIBLE, Validator.TRUE);
        boolean valid = Validator.runValidation(outputFile.getAbsolutePath(), settings);
        return valid;
    }
        
    private TransferDescription getTransferDescription(String modelName) throws IOException, Ili2cException {
        return getTransferDescription(modelName, null);
    }

    private TransferDescription getTransferDescription(String modelName, String[] respositories) throws IOException, Ili2cException {
        try (InputStream inputStream = ModelRepositoryCreator.class.getClassLoader().getResourceAsStream(modelName+".ili")) {
            if (inputStream == null) {
                throw new IOException("Resource not found");
            }

            Path tempDirectory = Files.createTempDirectory("ili_");
            Path targetPath = tempDirectory.resolve(modelName+".ili");
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            return getTransferDescription(targetPath, new String[] { targetPath.getParent().toString() });
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException(e);
        }        
    }
    
    private TransferDescription getTransferDescription(Path fileName, String[] respositories) throws IOException, Ili2cException {
        IliManager manager = new IliManager();        
        manager.setRepositories(respositories);
        
        ArrayList<String> ilifiles = new ArrayList<String>();
        ilifiles.add(fileName.toString());
        Configuration config = manager.getConfigWithFiles(ilifiles);
        TransferDescription td = Ili2c.runCompiler(config);
                
        if (td == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed");
        }
        
        return td;
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
