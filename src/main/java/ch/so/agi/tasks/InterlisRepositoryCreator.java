package ch.so.agi.tasks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.gradle.internal.impldep.org.junit.platform.commons.logging.LoggerFactory;
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

public class InterlisRepositoryCreator extends DefaultTask {
//    private Logger log = LoggerFactory.getLogger(this.getClass());
    private Logger log = Logging.getLogger(this.getClass());
    
    private TransferDescription tdRepository = null;
    private IoxWriter ioxWriter = null;
    private final static String ILI_TOPIC="IliRepository09.RepositoryIndex";
    private final static String ILI_CLASS=ILI_TOPIC+".ModelMetadata";
    private final static String ILI_STRUCT_MODELNAME="IliRepository09.ModelName_";
    private final static String BID="b1";

    @Input
    public Object modelsDir = null;

    @OutputFile
    public Object dataFile = null;

    @TaskAction
    public void writeIliModelsFile() {        
        if (modelsDir == null) {
            throw new IllegalArgumentException("modelsDir must not be null");
        }
        if (dataFile == null) {
            throw new IllegalArgumentException("dataFile must not be null");
        }

        try {
            createXmlFile(this.getProject().file(dataFile).getAbsolutePath(), this.getProject().file(modelsDir));
        } catch (Ili2cException | IoxException | IOException e) {
            e.printStackTrace();
            log.error("failed to create ilimodels.xml");
            log.error(e.getMessage());
            GradleException ge = new GradleException();
            throw ge;
        } 
    }

    private void createXmlFile(String outputFileName, File modelsDir) throws Ili2cException, IoxException, IOException {
        tdRepository = getTransferDescriptionFromModelName("IliRepository09");

        File outputFile = new File(outputFileName);
        ioxWriter = new XtfWriter(outputFile, tdRepository);
        ioxWriter.write(new ch.interlis.iox_j.StartTransferEvent("SOGIS-20190203", "", null));
        ioxWriter.write(new ch.interlis.iox_j.StartBasketEvent(ILI_TOPIC,BID));

        // Loop through all the local models found.
        String[] iliExt = new String[] {"ili"};
        IOFileFilter iliFilter = new SuffixFileFilter(iliExt, IOCase.INSENSITIVE);
        Iterator<File> it = FileUtils.iterateFiles(modelsDir, iliFilter, TrueFileFilter.INSTANCE);
        int i = 1;
        
        while (it.hasNext()) {
            File file = it.next();            
            
            // Abgelöste Modelle werden nicht im Fileindex (ilimodels.xml) 
            // aufgelistet. Auch weil wir immer neue Modellnamen machen und
            // keine precursorVersion-Semantik haben.
            // Andere Variante wäre isBrowseOnly=true.
            if(file.getAbsolutePath().toLowerCase().contains("replaced")) {
                continue;
            }
            
            TransferDescription td = getTransferDescriptionFromFileName(file.getAbsolutePath());            

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
                
                String technicalContact = lastModel.getMetaValue("technicalContact");
                if (technicalContact != null) {
                    if (technicalContact.contains("@") && !technicalContact.startsWith("mailto")) {
                        iomObj.setattrvalue("technicalContact", "mailto:agi@bd.so.ch");     
                    } else {
                        iomObj.setattrvalue("technicalContact", lastModel.getMetaValue("technicalContact"));     
                    }
                } else {
                    iomObj.setattrvalue("technicalContact", "mailto:agi@bd.so.ch");
                }
                
                String furtherInformation = lastModel.getMetaValue("furtherInformation"); 
                if (furtherInformation != null && furtherInformation.startsWith("http")) {
                    iomObj.setattrvalue("furtherInformation", furtherInformation);
                }
                
                try (InputStream is = Files.newInputStream(Paths.get(file.getAbsolutePath()))) {
                    String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
                    iomObj.setattrvalue("md5", md5);
                }

                // dependsOnModel
                for (Model model : lastModel.getImporting()) {
                    Iom_jObject iomObjDependsOnModel = new Iom_jObject(ILI_STRUCT_MODELNAME, null);
                    iomObjDependsOnModel.setattrvalue("value",  model.getName());
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

    }
    
    private TransferDescription getTransferDescriptionFromModelName(String iliModelName) throws Ili2cException {
        IliManager manager = new IliManager();
        String repositories[] = new String[] { "http://models.interlis.ch/", "http://models.kkgeo.ch/", "http://models.geo.admin.ch/", "http://geo.so.ch/models" };
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
    
    private TransferDescription getTransferDescriptionFromFileName(String fileName) throws Ili2cException {
        IliManager manager = new IliManager();
        // Lokales Verzeichnis muss als Repo verwendet werden, weil das Validierungsmodell das SO_FunctionsExt-Modell
        // importiert.
        // ./models/AGI/ -> Damit das Herstellen unserer Modellablage funktioniert. 
        // ./src/test/data/models/AGI/ -> Damit das Testing lokal funktioniert.
        // Muss entsprechend erweitert werden, falls andere Ämter auch gleiche Fälle haben.
        String repositories[] = new String[] { 
                // TODO: Muss dynamisch werden!
                "http://models.interlis.ch/", 
                "http://models.kkgeo.ch/", 
                "http://models.geo.admin.ch/", 
                "./models/AGI/", 
                "./models/ARP/", 
                "./models/ARP/replaced/", 
                "./src/test/data/models/AGI/", 
                "./src/test/data/models/ARP/", 
                "./src/test/data/models/ARP/replaced/"        
            };
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
}
