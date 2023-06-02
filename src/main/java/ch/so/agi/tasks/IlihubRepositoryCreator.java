package ch.so.agi.tasks;

import java.io.File;
import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;

public class IlihubRepositoryCreator extends DefaultTask {
    private Logger log = Logging.getLogger(this.getClass());
    
    private TransferDescription tdRepository = null;
    private IoxWriter ioxWriter = null;
    
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
    
    @InputFile
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

    private boolean createXmlFile(String outputFileName, File modelsDir) throws Ili2cException, IoxException, IOException {

        return false;
    }

}
